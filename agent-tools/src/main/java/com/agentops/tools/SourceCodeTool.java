package com.agentops.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 源码阅读工具 — 读取代码仓库中的源文件内容，用于异常诊断时定位和分析源码。
 *
 * <p>注册为名为 {@code read-source} 的工具，LLM 可通过它读取堆栈中项目代码帧对应的源文件，
 * 理解异常发生位置的代码逻辑。这是诊断流程的<strong>核心工具</strong> — git-blame 等工具
 * 仅用于辅助定位最近变更，而理解源码本身才是根因分析的关键。
 *
 * <p>输出包含行号，支持通过 {@code startLine}/{@code endLine} 限定读取范围。
 * 单次最多返回 {@link #MAX_LINES} 行，防止输出过长。
 */
public class SourceCodeTool {

    /** 单次读取的最大行数 */
    static final int MAX_LINES = 200;

    private final String repoPath;

    public SourceCodeTool(String repoPath) {
        this.repoPath = repoPath;
    }

    /** read-source 工具定义 */
    public static ToolDefinition definition() {
        return new ToolDefinition("read-source",
                "读取源代码文件内容（带行号），用于分析异常堆栈中项目代码的源码逻辑。" +
                        "这是诊断分析的核心工具：先读取源码理解代码，再用 git-blame 辅助定位变更。",
                Map.of("type", "object",
                        "properties", Map.of(
                                "filePath", Map.of("type", "string",
                                        "description", "源文件路径（相对于仓库根目录），如 src/main/java/com/example/OrderService.java"),
                                "startLine", Map.of("type", "integer",
                                        "description", "起始行号（可选，默认从第 1 行开始）"),
                                "endLine", Map.of("type", "integer",
                                        "description", "结束行号（可选，默认到文件末尾）")
                        ),
                        "required", List.of("filePath")));
    }

    /** read-source 执行器 */
    public ToolExecutor executor() {
        return args -> {
            String filePath = (String) args.get("filePath");
            if (filePath == null || filePath.isBlank()) {
                return ToolResult.failure("filePath 不能为空");
            }

            Integer startLine = args.containsKey("startLine")
                    ? ((Number) args.get("startLine")).intValue() : null;
            Integer endLine = args.containsKey("endLine")
                    ? ((Number) args.get("endLine")).intValue() : null;

            return readFile(repoPath, filePath, startLine, endLine);
        };
    }

    /** 读取文件内容并添加行号，支持行范围过滤 */
    static ToolResult readFile(String repoPath, String filePath, Integer startLine, Integer endLine) {
        Path fullPath = Path.of(repoPath, filePath);
        if (!Files.exists(fullPath)) {
            return ToolResult.failure("文件不存在: " + filePath);
        }
        if (Files.isDirectory(fullPath)) {
            return ToolResult.failure("路径是目录，不是文件: " + filePath);
        }

        try {
            List<String> lines = Files.readAllLines(fullPath, StandardCharsets.UTF_8);
            int totalLines = lines.size();

            // 计算实际读取范围
            int start = startLine != null ? Math.max(1, startLine) : 1;
            int end = endLine != null ? Math.min(totalLines, endLine) : totalLines;
            if (start > totalLines) {
                return ToolResult.failure("起始行号 " + start + " 超出文件总行数 " + totalLines);
            }

            // 限制最大行数
            if (end - start + 1 > MAX_LINES) {
                end = start + MAX_LINES - 1;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("文件: ").append(filePath).append(" (共 ").append(totalLines).append(" 行)");
            sb.append("\n显示: 第 ").append(start).append("-").append(end).append(" 行");
            sb.append("\n\n");

            for (int i = start - 1; i < end && i < totalLines; i++) {
                sb.append(String.format("%4d| %s%n", i + 1, lines.get(i)));
            }

            if (end < totalLines) {
                sb.append("\n... (还有 ").append(totalLines - end).append(" 行未显示，可调整 startLine/endLine 继续)");
            }

            return ToolResult.success(sb.toString());
        } catch (IOException e) {
            return ToolResult.failure("读取文件失败: " + e.getMessage());
        }
    }
}
