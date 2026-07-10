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
 * SourceCodeTool 单元测试。
 *
 * <p>使用 JUnit {@code @TempDir} 创建临时目录作为仓库根路径，
 * 验证 read-source 工具的文件读取、行号添加、范围过滤和错误处理。
 */
class SourceCodeToolTest {

    @TempDir
    Path tempRepo;

    @Test
    @DisplayName("读取完整文件 — 输出包含行号")
    void readFullFile() throws IOException {
        Path file = tempRepo.resolve("src/Main.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"hello\");\n    }\n}\n");

        ToolResult result = SourceCodeTool.readFile(
                tempRepo.toString(), "src/Main.java", null, null);

        assertTrue(result.success());
        assertTrue(result.output().contains("   1| public class Main {"));
        assertTrue(result.output().contains("   3|         System.out.println(\"hello\");"));
        assertTrue(result.output().contains("共 5 行"));
    }

    @Test
    @DisplayName("按行范围读取 — 只返回指定范围的行")
    void readWithLineRange() throws IOException {
        Path file = tempRepo.resolve("Service.java");
        Files.writeString(file, "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10\n");

        ToolResult result = SourceCodeTool.readFile(
                tempRepo.toString(), "Service.java", 3, 6);

        assertTrue(result.success());
        assertTrue(result.output().contains("   3| line3"));
        assertTrue(result.output().contains("   6| line6"));
        assertFalse(result.output().contains("line2"));
        assertFalse(result.output().contains("line7"));
        assertTrue(result.output().contains("第 3-6 行"));
    }

    @Test
    @DisplayName("文件不存在 — 返回失败结果")
    void fileNotFound() {
        ToolResult result = SourceCodeTool.readFile(
                tempRepo.toString(), "nonexistent/File.java", null, null);

        assertFalse(result.success());
        assertTrue(result.error().contains("文件不存在"));
    }

    @Test
    @DisplayName("路径是目录 — 返回失败结果")
    void directoryInsteadOfFile() throws IOException {
        Files.createDirectory(tempRepo.resolve("src"));

        ToolResult result = SourceCodeTool.readFile(
                tempRepo.toString(), "src", null, null);

        assertFalse(result.success());
        assertTrue(result.error().contains("目录"));
    }

    @Test
    @DisplayName("起始行号超出文件总行数 — 返回失败结果")
    void startLineBeyondTotal() throws IOException {
        Path file = tempRepo.resolve("Small.java");
        Files.writeString(file, "only one line\n");

        ToolResult result = SourceCodeTool.readFile(
                tempRepo.toString(), "Small.java", 100, null);

        assertFalse(result.success());
        assertTrue(result.error().contains("超出文件总行数"));
    }

    @Test
    @DisplayName("超过最大行数限制 — 截断并提示未显示行数")
    void maxLinesLimit() throws IOException {
        Path file = tempRepo.resolve("Big.java");
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 300; i++) {
            content.append("line").append(i).append("\n");
        }
        Files.writeString(file, content.toString());

        ToolResult result = SourceCodeTool.readFile(
                tempRepo.toString(), "Big.java", null, null);

        assertTrue(result.success());
        assertTrue(result.output().contains("还有 100 行未显示"));
        // 验证只输出了 MAX_LINES 行
        long lineCount = result.output().lines()
                .filter(l -> l.matches("^\\s*\\d+\\|.*"))
                .count();
        assertEquals(SourceCodeTool.MAX_LINES, lineCount);
    }

    @Test
    @DisplayName("工具定义 — 名称和参数正确")
    void toolDefinition() {
        ToolDefinition def = SourceCodeTool.definition();

        assertEquals("read-source", def.name());
        assertTrue(def.description().contains("源代码"));
        assertNotNull(def.parameters().get("properties"));
    }

    @Test
    @DisplayName("执行器 — 通过 executor 调用读取文件")
    void executorReadsFile() throws IOException {
        Path file = tempRepo.resolve("Calculator.java");
        Files.writeString(file, "public class Calculator {\n    int add(int a, int b) {\n        return a + b;\n    }\n}\n");

        SourceCodeTool tool = new SourceCodeTool(tempRepo.toString());
        ToolExecutor executor = tool.executor();

        ToolResult result = executor.execute(Map.of("filePath", "Calculator.java"));

        assertTrue(result.success());
        assertTrue(result.output().contains("add"));
    }

    @Test
    @DisplayName("执行器 — filePath 为空时返回失败")
    void executorEmptyPath() {
        SourceCodeTool tool = new SourceCodeTool(tempRepo.toString());
        ToolResult result = tool.executor().execute(Map.of("filePath", ""));

        assertFalse(result.success());
        assertTrue(result.error().contains("不能为空"));
    }
}
