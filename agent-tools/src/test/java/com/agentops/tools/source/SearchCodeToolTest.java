package com.agentops.tools.source;

import com.agentops.tools.core.ToolDefinition;
import com.agentops.tools.core.ToolExecutor;
import com.agentops.tools.core.ToolResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SearchCodeTool 单元测试。
 *
 * <p>使用 JUnit {@code @TempDir} 创建临时仓库，写入含目标字符串的样例文件，
 * 验证字面量匹配、正则匹配、文件过滤、maxResults 截断、目录跳过和长行截断。
 */
class SearchCodeToolTest {

    @TempDir
    Path tempRepo;

    /** 写入 OrderService.java — 含日志和异常抛出 */
    private void writeOrderService() throws IOException {
        Path file = tempRepo.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                package com.example;

                import org.slf4j.Logger;

                public class OrderService {
                    private static final Logger log = org.slf4j.LoggerFactory.getLogger(OrderService.class);

                    public void createOrder(String orderId) {
                        if (orderId == null) {
                            throw new BusinessException("订单创建失败: " + orderId);
                        }
                        log.error("订单创建失败", orderId);
                    }
                }
                """);
    }

    @Test
    @DisplayName("字面量匹配 — 中文文本命中")
    void literalMatch() throws IOException {
        writeOrderService();

        ToolResult result = SearchCodeTool.search(tempRepo.toString(), "订单创建失败", "*.java", 20);

        assertTrue(result.success());
        assertTrue(result.output().contains("OrderService.java"));
        // 应命中两行（throw + log.error）
        assertTrue(result.output().contains("共 2 条"));
        long matchEntries = result.output().lines().filter(l -> l.matches("^\\[\\d+\\].*")).count();
        assertEquals(2, matchEntries);
    }

    @Test
    @DisplayName("正则匹配 — throw new \\w+Exception 命中")
    void regexMatch() throws IOException {
        writeOrderService();

        ToolResult result = SearchCodeTool.search(tempRepo.toString(), "throw new \\w+Exception", "*.java", 20);

        assertTrue(result.success());
        assertTrue(result.output().contains("throw new BusinessException"));
    }

    @Test
    @DisplayName("大小写敏感 — 默认大小写敏感，需 (?i) 前缀忽略大小写")
    void caseSensitive() throws IOException {
        writeOrderService();

        // 大写 SEARCH 不应命中小写 search
        ToolResult sensitive = SearchCodeTool.search(tempRepo.toString(), "ORDERSERVICE", "*.java", 20);
        assertFalse(sensitive.success());

        // (?i) 前缀应命中
        ToolResult insensitive = SearchCodeTool.search(tempRepo.toString(), "(?i)orderservice", "*.java", 20);
        assertTrue(insensitive.success());
    }

    @Test
    @DisplayName("filePattern 过滤 — *.xml 不命中 .java 文件")
    void filePatternFilter() throws IOException {
        writeOrderService();

        ToolResult result = SearchCodeTool.search(tempRepo.toString(), "订单创建失败", "*.xml", 20);

        assertFalse(result.success());
        assertTrue(result.error().contains("未找到匹配的代码"));
    }

    @Test
    @DisplayName("maxResults 截断 — 超过限制只返回前 N 条")
    void maxResultsTruncation() throws IOException {
        Path file = tempRepo.resolve("ManyMatches.java");
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            content.append("String target").append(i).append(" = \"订单创建失败\";\n");
        }
        Files.writeString(file, content.toString());

        ToolResult result = SearchCodeTool.search(tempRepo.toString(), "订单创建失败", "*.java", 3);

        assertTrue(result.success());
        // 输出应标注共 3 条
        assertTrue(result.output().contains("共 3 条"));
    }

    @Test
    @DisplayName("跳过 target 目录 — target 下的文件不被扫描")
    void skipsTargetDirectory() throws IOException {
        writeOrderService();
        // 在 target 下放一个含 pattern 的文件
        Path junk = tempRepo.resolve("target/classes/Junk.java");
        Files.createDirectories(junk.getParent());
        Files.writeString(junk, "class Junk { String s = \"订单创建失败\"; }");

        ToolResult result = SearchCodeTool.search(tempRepo.toString(), "订单创建失败", "*.java", 20);

        assertTrue(result.success());
        // 只应命中 OrderService.java，不含 Junk.java
        assertFalse(result.output().contains("Junk.java"));
    }

    @Test
    @DisplayName("不命中 — 返回失败并提示换关键词")
    void noMatch() throws IOException {
        writeOrderService();

        ToolResult result = SearchCodeTool.search(tempRepo.toString(), "这个字符串不存在", "*.java", 20);

        assertFalse(result.success());
        assertTrue(result.error().contains("未找到匹配的代码"));
    }

    @Test
    @DisplayName("长行截断 — 超过 200 字符的行加 ... 后缀")
    void longLineTruncation() throws IOException {
        Path file = tempRepo.resolve("LongLine.java");
        StringBuilder longLine = new StringBuilder("String marker = \"");
        while (longLine.length() < 300) {
            longLine.append("x");
        }
        longLine.append("\";");
        Files.writeString(file, longLine.toString());

        ToolResult result = SearchCodeTool.search(tempRepo.toString(), "marker", "*.java", 20);

        assertTrue(result.success());
        assertTrue(result.output().contains("..."));
    }

    @Test
    @DisplayName("无效正则 — 返回失败")
    void invalidRegex() {
        ToolResult result = SearchCodeTool.search(tempRepo.toString(), "[unclosed", "*.java", 20);

        assertFalse(result.success());
        assertTrue(result.error().contains("正则表达式无效"));
    }

    @Test
    @DisplayName("仓库不存在 — 返回失败")
    void repoNotExist() {
        ToolResult result = SearchCodeTool.search("/nonexistent/path", "anything", "*.java", 20);

        assertFalse(result.success());
        assertTrue(result.error().contains("仓库路径不存在"));
    }

    @Test
    @DisplayName("工具定义 — 名称和参数正确")
    void toolDefinition() {
        ToolDefinition def = SearchCodeTool.definition();

        assertEquals("search-code", def.name());
        assertTrue(def.description().contains("搜索"));
        assertNotNull(def.parameters().get("properties"));
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) def.parameters().get("required");
        assertEquals(java.util.List.of("pattern"), required);
    }

    @Test
    @DisplayName("执行器 — 端到端调用搜索")
    void executorEndToEnd() throws IOException {
        writeOrderService();
        SearchCodeTool tool = new SearchCodeTool(tempRepo.toString());

        ToolResult result = tool.executor().execute(Map.of("pattern", "订单创建失败"));

        assertTrue(result.success());
        assertTrue(result.output().contains("OrderService.java"));
    }

    @Test
    @DisplayName("执行器 — pattern 为空时返回失败")
    void executorEmptyPattern() {
        SearchCodeTool tool = new SearchCodeTool(tempRepo.toString());
        ToolResult result = tool.executor().execute(Map.of("pattern", ""));

        assertFalse(result.success());
        assertTrue(result.error().contains("不能为空"));
    }

    @Test
    @DisplayName("执行器 — 使用默认 filePattern（*.java）和默认 maxResults（20）")
    void executorDefaultParams() throws IOException {
        writeOrderService();
        SearchCodeTool tool = new SearchCodeTool(tempRepo.toString());

        // 只传 pattern，其他参数走默认
        ToolResult result = tool.executor().execute(Map.of("pattern", "BusinessException"));

        assertTrue(result.success());
        assertTrue(result.output().contains("BusinessException"));
    }
}
