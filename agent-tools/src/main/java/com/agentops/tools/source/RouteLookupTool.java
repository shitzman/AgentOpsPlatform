package com.agentops.tools.source;

import com.agentops.tools.core.ToolDefinition;
import com.agentops.tools.core.ToolExecutor;
import com.agentops.tools.core.ToolResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ParseProblemException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 路由反查工具 — 根据接口路径定位 Spring MVC Controller 方法的源码位置。
 *
 * <p>注册为 {@code route-lookup} 工具。诊断时当日志/访问日志中出现接口路径
 * （如 {@code POST /api/orders}），LLM 可调用此工具反查到处理该请求的
 * Controller 类、方法、源码文件和行号，再用 {@code read-source} 读取源码。
 *
 * <p>实现：用 JavaParser 扫描仓库内所有 {@code .java} 文件，提取
 * {@code @RequestMapping} / {@code @GetMapping} / {@code @PostMapping} 等
 * Spring Web 注解，组合 class-level 前缀和 method-level 路径，构建路由表。
 * 路由表在实例内懒缓存（首次调用构建，后续复用），不监听文件变更
 * （v1 限制：进程重启即刷新缓存）。
 *
 * <h3>匹配策略</h3>
 * <ul>
 *   <li>精确匹配优先：{@code entry.fullPath().equals(queryPath)}</li>
 *   <li>无精确匹配时做前缀匹配：{@code /api/orders/123} 可匹配到 {@code /api/orders}</li>
 *   <li>{@code method} 可选，用于过滤；未指定时返回所有 HTTP method</li>
 *   <li>class-level {@code @RequestMapping} 不带 {@code method} 时视为 ANY，匹配任意请求方法</li>
 * </ul>
 */
public class RouteLookupTool {

    private static final Logger log = LoggerFactory.getLogger(RouteLookupTool.class);

    /** 单次最多返回的路由条目数 */
    static final int MAX_RESULTS = 10;

    /** 需要跳过的目录（非源码或构建产物） */
    private static final List<String> SKIP_DIRS = List.of(".git", "target", "build", "node_modules", ".idea", ".vscode");

    private final String repoPath;

    /** 路由表缓存（懒构建，volatile + 双检锁） */
    private volatile List<RouteEntry> cachedRoutes;

    public RouteLookupTool(String repoPath) {
        this.repoPath = repoPath;
    }

    /** route-lookup 工具定义 */
    public static ToolDefinition definition() {
        return new ToolDefinition("route-lookup",
                "根据接口路径反查 Spring MVC Controller 方法的源码位置。" +
                        "当日志/访问日志中出现接口路径（如 POST /api/orders）时，" +
                        "用此工具定位处理该请求的 Controller 方法，再用 read-source 读取源码。",
                Map.of("type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string",
                                        "description", "接口路径，如 /api/orders 或 /api/orders/123"),
                                "method", Map.of("type", "string",
                                        "description", "HTTP 方法（可选，如 GET/POST/PUT/DELETE）")
                        ),
                        "required", List.of("path")));
    }

    /** route-lookup 执行器 — 使用缓存的路由表 */
    public ToolExecutor executor() {
        return args -> {
            String path = (String) args.get("path");
            if (path == null || path.isBlank()) {
                return ToolResult.failure("path 不能为空");
            }
            String method = args.containsKey("method") ? ((String) args.get("method")).toUpperCase() : null;
            return lookup(getRoutes(), path, method);
        };
    }

    /** 获取路由表（懒构建 + 缓存） */
    private List<RouteEntry> getRoutes() {
        List<RouteEntry> routes = cachedRoutes;
        if (routes == null) {
            synchronized (this) {
                routes = cachedRoutes;
                if (routes == null) {
                    long start = System.nanoTime();
                    routes = buildRouteTable(repoPath);
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    cachedRoutes = routes;
                    log.info("[getRoutes] route table built: repoPath={}, routes={}, elapsedMs={}",
                            repoPath, routes.size(), elapsedMs);
                }
            }
        }
        return routes;
    }

    // ---- 核心静态方法（供测试直接调用，绕过缓存） ----

    /** 扫描仓库构建路由表（无缓存，每次重新解析） */
    static List<RouteEntry> buildRouteTable(String repoPath) {
        List<RouteEntry> routes = new ArrayList<>();
        Path root = Path.of(repoPath);
        if (!Files.exists(root)) {
            return routes;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java") && Files.isRegularFile(p))
                    .filter(p -> !isInSkippedDir(root, p))
                    .forEach(p -> parseFileForRoutes(root, p, routes));
        } catch (IOException e) {
            return routes;
        }
        return routes;
    }

    /** 在路由表中查找匹配项，返回格式化结果 */
    static ToolResult lookup(List<RouteEntry> routes, String queryPath, String method) {
        List<RouteEntry> matches = matchRoutes(routes, queryPath, method);
        log.debug("[lookup] queryPath={}, method={}, matches={}", queryPath, method, matches.size());
        if (matches.isEmpty()) {
            return ToolResult.failure("未找到匹配的路由: path=" + queryPath
                    + (method != null ? ", method=" + method : "")
                    + "。可尝试调整路径或检查是否为 Spring MVC 接口。");
        }
        return ToolResult.success(formatResults(queryPath, method, matches));
    }

    // ---- 路由表构建 ----

    /** 判断文件是否在需跳过的目录下 */
    private static boolean isInSkippedDir(Path root, Path file) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            if (SKIP_DIRS.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    /** 解析单个 Java 文件，提取路由条目加入 routes */
    private static void parseFileForRoutes(Path root, Path file, List<RouteEntry> routes) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file.toFile());
            String relativePath = root.relativize(file).toString().replace('\\', '/');

            for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                List<String> prefixes = extractPaths(cls.getAnnotationByName("RequestMapping").orElse(null));
                if (prefixes.isEmpty()) {
                    prefixes = List.of("");
                }
                for (MethodDeclaration md : cls.findAll(MethodDeclaration.class)) {
                    for (Map.Entry<String, String> ann : findMappingAnnotations(md)) {
                        String httpMethod = ann.getValue();
                        if ("ANY".equals(httpMethod)) {
                            // @RequestMapping at method level — extract method attribute if present
                            AnnotationExpr reqAnn = md.getAnnotationByName("RequestMapping").orElse(null);
                            httpMethod = extractMethodAttribute(reqAnn);
                        }
                        List<String> methodPaths = extractPaths(md.getAnnotationByName(ann.getKey()).orElse(null));
                        if (methodPaths.isEmpty()) {
                            methodPaths = List.of("");
                        }
                        for (String prefix : prefixes) {
                            for (String suffix : methodPaths) {
                                routes.add(new RouteEntry(
                                        httpMethod,
                                        combinePath(prefix, suffix),
                                        cls.getNameAsString(),
                                        methodSignature(md),
                                        relativePath,
                                        md.getBegin().map(p -> p.line).orElse(0)
                                ));
                            }
                        }
                    }
                }
            }
        } catch (ParseProblemException e) {
            // 语法错误的文件跳过
        } catch (IOException e) {
            // 读取失败的文件跳过
        }
    }

    /** 查找方法上的 mapping 注解，返回 注解名 → HTTP方法 的映射 */
    private static List<Map.Entry<String, String>> findMappingAnnotations(MethodDeclaration md) {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        if (md.isAnnotationPresent("GetMapping")) {
            result.add(Map.entry("GetMapping", "GET"));
        }
        if (md.isAnnotationPresent("PostMapping")) {
            result.add(Map.entry("PostMapping", "POST"));
        }
        if (md.isAnnotationPresent("PutMapping")) {
            result.add(Map.entry("PutMapping", "PUT"));
        }
        if (md.isAnnotationPresent("DeleteMapping")) {
            result.add(Map.entry("DeleteMapping", "DELETE"));
        }
        if (md.isAnnotationPresent("PatchMapping")) {
            result.add(Map.entry("PatchMapping", "PATCH"));
        }
        if (md.isAnnotationPresent("RequestMapping")) {
            // method-level @RequestMapping — method attribute determines HTTP method
            result.add(Map.entry("RequestMapping", "ANY"));
        }
        return result;
    }

    /** 从 @RequestMapping 的 method 属性提取 HTTP 方法（如 RequestMethod.POST → POST），未指定返回 ANY */
    private static String extractMethodAttribute(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("method")) {
                    return methodExpressionToString(pair.getValue());
                }
            }
        }
        return "ANY";
    }

    /** 将 RequestMethod.POST 或 POST 形式的表达式转为方法字符串 */
    private static String methodExpressionToString(Expression expr) {
        if (expr instanceof FieldAccessExpr fae) {
            return fae.getNameAsString().toUpperCase();
        }
        if (expr instanceof NameExpr ne) {
            return ne.getNameAsString().toUpperCase();
        }
        return "ANY";
    }

    /** 从注解提取 path/value 字符串列表（支持单值、数组、value=/path= 两种属性名） */
    private static List<String> extractPaths(AnnotationExpr ann) {
        if (ann == null) {
            return List.of();
        }
        if (ann instanceof MarkerAnnotationExpr) {
            return List.of("");
        }
        if (ann instanceof SingleMemberAnnotationExpr single) {
            return expressionToStrings(single.getMemberValue());
        }
        if (ann instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                String name = pair.getNameAsString();
                if (name.equals("value") || name.equals("path")) {
                    return expressionToStrings(pair.getValue());
                }
            }
            return List.of("");
        }
        return List.of();
    }

    /** 将表达式转为字符串列表（StringLiteral → 单值，ArrayInitializer → 多值） */
    private static List<String> expressionToStrings(Expression expr) {
        if (expr instanceof StringLiteralExpr lit) {
            return List.of(lit.asString());
        }
        if (expr instanceof ArrayInitializerExpr arr) {
            List<String> result = new ArrayList<>();
            for (Expression e : arr.getValues()) {
                if (e instanceof StringLiteralExpr lit) {
                    result.add(lit.asString());
                }
            }
            return result;
        }
        return List.of();
    }

    /** 组合 class-level 前缀和 method-level 路径，归一化斜杠 */
    static String combinePath(String prefix, String suffix) {
        String p = prefix == null ? "" : prefix.trim();
        String s = suffix == null ? "" : suffix.trim();
        String combined = p + "/" + s;
        String result = combined.replaceAll("/+", "/");
        if (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        return result;
    }

    /** 构造方法签名（方法名 + 参数类型列表） */
    private static String methodSignature(MethodDeclaration md) {
        String params = md.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(", "));
        return md.getNameAsString() + "(" + params + ")";
    }

    // ---- 匹配 ----

    /** 匹配路由：精确优先，无精确时前缀匹配；可选 method 过滤 */
    static List<RouteEntry> matchRoutes(List<RouteEntry> routes, String queryPath, String method) {
        String normalizedQuery = normalizeQueryPath(queryPath);
        List<RouteEntry> exact = routes.stream()
                .filter(r -> r.fullPath().equals(normalizedQuery))
                .filter(r -> methodMatches(r, method))
                .toList();
        if (!exact.isEmpty()) {
            return limit(exact);
        }
        List<RouteEntry> prefix = routes.stream()
                .filter(r -> !r.fullPath().equals("/") && normalizedQuery.startsWith(r.fullPath() + "/"))
                .filter(r -> methodMatches(r, method))
                .toList();
        return limit(prefix);
    }

    private static boolean methodMatches(RouteEntry r, String method) {
        if (method == null) {
            return true;
        }
        return "ANY".equals(r.httpMethod()) || r.httpMethod().equals(method);
    }

    private static List<RouteEntry> limit(List<RouteEntry> list) {
        return list.size() > MAX_RESULTS ? list.subList(0, MAX_RESULTS) : list;
    }

    private static String normalizeQueryPath(String path) {
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        p = p.replaceAll("/+", "/");
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    // ---- 输出格式化 ----

    private static String formatResults(String queryPath, String method, List<RouteEntry> matches) {
        StringBuilder sb = new StringBuilder();
        sb.append("路由匹配结果 (path=").append(queryPath);
        if (method != null) {
            sb.append(", method=").append(method);
        }
        sb.append(", 共 ").append(matches.size()).append(" 条):\n\n");
        for (int i = 0; i < matches.size(); i++) {
            RouteEntry r = matches.get(i);
            sb.append("[").append(i + 1).append("] ").append(r.httpMethod()).append(" ").append(r.fullPath()).append("\n");
            sb.append("    ").append(r.className()).append(".").append(r.methodSignature()).append("\n");
            sb.append("    文件: ").append(r.filePath()).append(":").append(r.lineNumber()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 路由条目 — 不可变 record */
    record RouteEntry(
            String httpMethod,
            String fullPath,
            String className,
            String methodSignature,
            String filePath,
            int lineNumber) {}
}
