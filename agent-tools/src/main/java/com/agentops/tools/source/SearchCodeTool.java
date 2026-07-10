package com.agentops.tools.source;

import com.agentops.tools.core.ToolDefinition;
import com.agentops.tools.core.ToolExecutor;
import com.agentops.tools.core.ToolResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * 代码搜索工具 — 按正则在代码仓库中搜索源码,定位日志/异常消息的抛出位置。
 *
 * <p>注册为 {@code search-code} 工具。诊断时当日志中出现错误消息字面量
 * （如 "订单创建失败"）或异常消息模板时,LLM 可调用此工具找到抛出该日志/异常的源码位置,
 * 再用 {@code read-source} 读取上下文。
 *
 * <h3>实现</h3>
 * <ul>
 *   <li>纯 Java NIO 文件遍历 + {@link Pattern} 正则匹配,无外部进程依赖</li>
 *   <li>跳过 {@code .git} / {@code target} / {@code build} / {@code node_modules} 等非源码目录</li>
 *   <li>按 {@code filePattern} 通配过滤文件名（默认 {@code *.java}）</li>
 *   <li>行内容超 {@link #MAX_LINE_LENGTH} 字符截断加 {@code ...}</li>
 *   <li>达到 {@code maxResults} 提前终止</li>
 *   <li>不命中返回 failure，引导 LLM 换关键词</li>
 * </ul>
 */
public class SearchCodeTool {

    private static final Logger log = LoggerFactory.getLogger(SearchCodeTool.class);

    /** 单次最多返回的匹配条目数上限 */
    static final int HARD_MAX_RESULTS = 50;

    /** 行内容截断长度 */
    static final int MAX_LINE_LENGTH = 200;

    /** 需要跳过的目录 */
    private static final List<String> SKIP_DIRS = List.of(".git", "target", "build", "node_modules", ".idea", ".vscode");

    /** 默认文件通配模式 */
    private static final String DEFAULT_FILE_PATTERN = "*.java";

    private final String repoPath;

    public SearchCodeTool(String repoPath) {
        this.repoPath = repoPath;
    }

    /** search-code 工具定义 */
    public static ToolDefinition definition() {
        return new ToolDefinition("search-code",
                "在代码仓库中搜索字符串或正则，定位日志/异常消息的抛出位置。" +
                        "当日志中出现错误消息字面量（如 \"订单创建失败\"）或异常消息模板时，" +
                        "用此工具找到抛出该日志/异常的源码位置。",
                Map.of("type", "object",
                        "properties", Map.of(
                                "pattern", Map.of("type", "string",
                                        "description", "搜索模式（正则表达式）。可用 (?i) 前缀控制大小写。如：订单创建失败、throw new \\w+Exception"),
                                "filePattern", Map.of("type", "string",
                                        "description", "文件名通配模式（可选，默认 *.java）。如 *.java、*.kt、*.xml"),
                                "maxResults", Map.of("type", "integer",
                                        "description", "最多返回条数（可选，默认 20）")
                        ),
                        "required", List.of("pattern")));
    }

    /** search-code 执行器 */
    public ToolExecutor executor() {
        return args -> {
            String pattern = (String) args.get("pattern");
            if (pattern == null || pattern.isBlank()) {
                return ToolResult.failure("pattern 不能为空");
            }
            String filePattern = args.containsKey("filePattern")
                    ? (String) args.get("filePattern") : DEFAULT_FILE_PATTERN;
            int maxResults = args.containsKey("maxResults")
                    ? ((Number) args.get("maxResults")).intValue() : 20;
            return search(repoPath, pattern, filePattern, maxResults);
        };
    }

    // ---- 核心静态方法（供测试直接调用） ----

    /** 在仓库中搜索匹配的代码行 */
    static ToolResult search(String repoPath, String pattern, String filePattern, int maxResults) {
        int limit = Math.min(Math.max(maxResults, 1), HARD_MAX_RESULTS);
        String normalizedFilePattern = (filePattern == null || filePattern.isBlank())
                ? DEFAULT_FILE_PATTERN : filePattern;

        Pattern regex;
        try {
            regex = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return ToolResult.failure("正则表达式无效: " + e.getMessage());
        }

        Path root = Path.of(repoPath);
        if (!Files.exists(root)) {
            return ToolResult.failure("仓库路径不存在: " + repoPath);
        }

        PathMatcher matcher = root.getFileSystem().getPathMatcher("glob:" + normalizedFilePattern);
        List<Match> matches = new ArrayList<>();
        long start = System.nanoTime();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p))
                    .filter(p -> !isInSkippedDir(root, p))
                    .filter(p -> matchesFileName(p, matcher, normalizedFilePattern))
                    .forEach(p -> scanFile(root, p, regex, limit, matches));
        } catch (IOException e) {
            return ToolResult.failure("遍历仓库失败: " + e.getMessage());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.debug("[search] pattern={}, filePattern={}, matches={}, elapsedMs={}",
                pattern, normalizedFilePattern, matches.size(), elapsedMs);

        if (matches.isEmpty()) {
            return ToolResult.failure("未找到匹配的代码: pattern=" + pattern
                    + ", filePattern=" + normalizedFilePattern
                    + "。可尝试调整正则或更换 filePattern。");
        }
        return ToolResult.success(formatResults(pattern, normalizedFilePattern, matches));
    }

    // ---- 文件过滤 ----

    private static boolean isInSkippedDir(Path root, Path file) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            if (SKIP_DIRS.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    /** 用 PathMatcher 匹配文件名；matcher 异常时回退到后缀匹配 */
    private static boolean matchesFileName(Path file, PathMatcher matcher, String filePattern) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return false;
        }
        try {
            return matcher.matches(fileName);
        } catch (Exception e) {
            // glob 模式异常时按后缀匹配兜底
            if (filePattern != null && filePattern.startsWith("*.")) {
                return fileName.toString().endsWith(filePattern.substring(1));
            }
            return false;
        }
    }

    // ---- 逐文件扫描 ----

    private static void scanFile(Path root, Path file, Pattern regex, int limit, List<Match> matches) {
        if (matches.size() >= limit) {
            return;
        }
        String relativePath = root.relativize(file).toString().replace('\\', '/');
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            int[] lineNum = {0};
            lines.takeWhile(l -> matches.size() < limit)
                    .forEach(line -> {
                        lineNum[0]++;
                        if (regex.matcher(line).find()) {
                            matches.add(new Match(relativePath, lineNum[0], truncate(line)));
                        }
                    });
        } catch (IOException | UncheckedIOException e) {
            // 读取失败的文件跳过（可能是二进制文件或编码问题）
        }
    }

    /** 行内容超长截断 */
    private static String truncate(String line) {
        String trimmed = line.trim();
        if (trimmed.length() <= MAX_LINE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_LINE_LENGTH) + "...";
    }

    // ---- 输出格式化 ----

    private static String formatResults(String pattern, String filePattern, List<Match> matches) {
        StringBuilder sb = new StringBuilder();
        sb.append("代码搜索结果 (pattern=").append(pattern)
                .append(", filePattern=").append(filePattern)
                .append(", 共 ").append(matches.size()).append(" 条):\n\n");
        for (int i = 0; i < matches.size(); i++) {
            Match m = matches.get(i);
            sb.append("[").append(i + 1).append("] ").append(m.filePath).append(":").append(m.lineNumber).append("\n");
            sb.append("    ").append(m.lineContent).append("\n");
            if (i < matches.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** 匹配条目 — 不可变 record */
    record Match(String filePath, int lineNumber, String lineContent) {}
}
