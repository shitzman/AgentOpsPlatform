package com.agentops.tools.source;

import com.agentops.tools.core.ToolDefinition;
import com.agentops.tools.core.ToolExecutor;
import com.agentops.tools.core.ToolResult;
import com.agentops.tools.source.RouteLookupTool.RouteEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RouteLookupTool 单元测试。
 *
 * <p>使用 JUnit {@code @TempDir} 创建临时仓库，写入带 Spring Web 注解的样例 Controller，
 * 验证路由表构建、路径匹配、方法过滤和缓存行为。
 */
class RouteLookupToolTest {

    @TempDir
    Path tempRepo;

    /** 写入 OrderController — 带 class-level @RequestMapping("/api") + 多个 method 映射 */
    private void writeOrderController() throws IOException {
        Path file = tempRepo.resolve("src/main/java/com/example/OrderController.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                package com.example;

                import org.springframework.web.bind.annotation.*;

                @RestController
                @RequestMapping("/api")
                public class OrderController {

                    @PostMapping("/orders")
                    public String createOrder(OrderRequest req) {
                        return "ok";
                    }

                    @GetMapping("/orders/{id}")
                    public String getOrder(@PathVariable String id) {
                        return "ok";
                    }

                    @DeleteMapping("/orders/{id}")
                    public String deleteOrder(@PathVariable String id) {
                        return "ok";
                    }
                }
                """);
    }

    /** 写入 HealthController — 无 class-level @RequestMapping，直接 method 路径 */
    private void writeHealthController() throws IOException {
        Path file = tempRepo.resolve("src/main/java/com/example/HealthController.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                package com.example;

                import org.springframework.web.bind.annotation.*;

                @RestController
                public class HealthController {
                    @GetMapping("/health")
                    public String health() {
                        return "ok";
                    }
                }
                """);
    }

    @Test
    @DisplayName("构建路由表 — 提取 class + method 注解并组合路径")
    void buildRouteTableExtractsRoutes() throws IOException {
        writeOrderController();

        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        // 3 个方法映射
        assertEquals(3, routes.size());
        assertTrue(routes.stream().anyMatch(r -> r.fullPath().equals("/api/orders") && r.httpMethod().equals("POST")));
        assertTrue(routes.stream().anyMatch(r -> r.fullPath().equals("/api/orders/{id}") && r.httpMethod().equals("GET")));
        assertTrue(routes.stream().anyMatch(r -> r.fullPath().equals("/api/orders/{id}") && r.httpMethod().equals("DELETE")));
    }

    @Test
    @DisplayName("精确匹配 — POST /api/orders 命中 createOrder")
    void exactMatch() throws IOException {
        writeOrderController();
        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        ToolResult result = RouteLookupTool.lookup(routes, "/api/orders", "POST");

        assertTrue(result.success());
        assertTrue(result.output().contains("POST /api/orders"));
        assertTrue(result.output().contains("OrderController.createOrder"));
        assertTrue(result.output().contains("OrderController.java"));
    }

    @Test
    @DisplayName("前缀匹配 — POST /api/orders/123 无精确匹配时回退前缀匹配到 /api/orders")
    void prefixMatch() throws IOException {
        writeOrderController();
        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        ToolResult result = RouteLookupTool.lookup(routes, "/api/orders/123", "POST");

        assertTrue(result.success());
        // 前缀匹配应返回 /api/orders（因为 /api/orders/123 以 /api/orders/ 开头）
        assertTrue(result.output().contains("/api/orders"));
    }

    @Test
    @DisplayName("方法过滤 — GET /api/orders 不匹配 POST 方法")
    void methodFilterRejectsMismatch() throws IOException {
        writeOrderController();
        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        ToolResult result = RouteLookupTool.lookup(routes, "/api/orders", "GET");

        // /api/orders 只有 POST，GET 不匹配
        assertFalse(result.success());
        assertTrue(result.error().contains("未找到匹配的路由"));
    }

    @Test
    @DisplayName("方法匹配 — DELETE /api/orders/{id} 精确命中 deleteOrder")
    void methodMatchForTemplatePath() throws IOException {
        writeOrderController();
        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        ToolResult result = RouteLookupTool.lookup(routes, "/api/orders/{id}", "DELETE");

        assertTrue(result.success());
        assertTrue(result.output().contains("DELETE /api/orders/{id}"));
        assertTrue(result.output().contains("deleteOrder"));
    }

    @Test
    @DisplayName("不指定 method — 返回所有匹配路径的路由")
    void noMethodFilterReturnsAll() throws IOException {
        writeOrderController();
        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        ToolResult result = RouteLookupTool.lookup(routes, "/api/orders/{id}", null);

        assertTrue(result.success());
        // GET 和 DELETE 都匹配
        assertTrue(result.output().contains("GET /api/orders/{id}"));
        assertTrue(result.output().contains("DELETE /api/orders/{id}"));
    }

    @Test
    @DisplayName("无 class-level @RequestMapping — 直接使用 method 路径")
    void noClassLevelPrefix() throws IOException {
        writeHealthController();
        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        ToolResult result = RouteLookupTool.lookup(routes, "/health", "GET");

        assertTrue(result.success());
        assertTrue(result.output().contains("GET /health"));
        assertTrue(result.output().contains("HealthController.health"));
    }

    @Test
    @DisplayName("不存在的路径 — 返回失败")
    void pathNotFound() throws IOException {
        writeOrderController();
        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        ToolResult result = RouteLookupTool.lookup(routes, "/nonexistent", "POST");

        assertFalse(result.success());
        assertTrue(result.error().contains("未找到匹配的路由"));
    }

    @Test
    @DisplayName("空仓库 — 构建空路由表，查询返回失败")
    void emptyRepo() {
        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        assertTrue(routes.isEmpty());
        ToolResult result = RouteLookupTool.lookup(routes, "/api/anything", "GET");
        assertFalse(result.success());
    }

    @Test
    @DisplayName("跳过 target 目录 — 即使含注解也不被扫描")
    void skipsTargetDirectory() throws IOException {
        writeOrderController();
        // 在 target 下放一个 controller，不应被扫描到
        Path junk = tempRepo.resolve("target/classes/com/example/JunkController.java");
        Files.createDirectories(junk.getParent());
        Files.writeString(junk, """
                package com.example;
                @org.springframework.web.bind.annotation.RestController
                public class JunkController {
                    @org.springframework.web.bind.annotation.GetMapping("/junk")
                    public String junk() { return "junk"; }
                }
                """);

        List<RouteEntry> routes = RouteLookupTool.buildRouteTable(tempRepo.toString());

        // 只应扫到 OrderController 的 3 个路由，不含 /junk
        assertEquals(3, routes.size());
        assertFalse(routes.stream().anyMatch(r -> r.fullPath().equals("/junk")));
    }

    @Test
    @DisplayName("工具定义 — 名称和参数正确")
    void toolDefinition() {
        ToolDefinition def = RouteLookupTool.definition();

        assertEquals("route-lookup", def.name());
        assertTrue(def.description().contains("接口路径"));
        assertNotNull(def.parameters().get("properties"));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) def.parameters().get("required");
        assertEquals(List.of("path"), required);
    }

    @Test
    @DisplayName("执行器 — 端到端调用读取路由")
    void executorEndToEnd() throws IOException {
        writeOrderController();
        RouteLookupTool tool = new RouteLookupTool(tempRepo.toString());

        ToolResult result = tool.executor().execute(Map.of("path", "/api/orders", "method", "POST"));

        assertTrue(result.success());
        assertTrue(result.output().contains("POST /api/orders"));
    }

    @Test
    @DisplayName("执行器 — path 为空时返回失败")
    void executorEmptyPath() {
        RouteLookupTool tool = new RouteLookupTool(tempRepo.toString());
        ToolResult result = tool.executor().execute(Map.of("path", ""));

        assertFalse(result.success());
        assertTrue(result.error().contains("不能为空"));
    }

    @Test
    @DisplayName("缓存 — 两次调用返回一致结果且不报错")
    void cacheReturnsConsistentResults() throws IOException {
        writeOrderController();
        RouteLookupTool tool = new RouteLookupTool(tempRepo.toString());
        ToolExecutor executor = tool.executor();

        ToolResult first = executor.execute(Map.of("path", "/api/orders", "method", "POST"));
        ToolResult second = executor.execute(Map.of("path", "/api/orders", "method", "POST"));

        assertTrue(first.success());
        assertEquals(first.output(), second.output());
    }

    @Test
    @DisplayName("combinePath — 路径组合与归一化")
    void combinePathNormalization() {
        assertEquals("/api/orders", RouteLookupTool.combinePath("/api", "/orders"));
        assertEquals("/api/orders", RouteLookupTool.combinePath("/api/", "/orders"));
        assertEquals("/api/orders", RouteLookupTool.combinePath("/api", "orders"));
        assertEquals("/orders", RouteLookupTool.combinePath("", "/orders"));
        assertEquals("/api", RouteLookupTool.combinePath("/api", ""));
        assertEquals("/", RouteLookupTool.combinePath("", ""));
        assertEquals("/api/orders/detail", RouteLookupTool.combinePath("/api", "/orders/detail"));
    }
}
