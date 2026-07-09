package com.agentops.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件日志提供者 — 在服务器本地日志文件上执行关键词搜索。
 *
 * <p>从 {@link LogSourceConfig#properties()} 中读取 {@code filePath}，
 * 逐行扫描并返回包含关键词的行。适用于开发环境或日志文件可直接访问的场景。
 *
 * <p>大文件处理：最多读取 10000 行，超出部分自动截断。
 */
public class FileLogProvider implements LogProvider {

    private static final int MAX_LINES = 10000;

    @Override
    public ToolResult search(Map<String, Object> args, LogSourceConfig config) {
        String keyword = (String) args.get("keyword");
        String filePath = config.property("filePath", "");
        int limit = args.containsKey("limit")
                ? ((Number) args.get("limit")).intValue() : 10;

        if (filePath.isBlank()) {
            return ToolResult.failure("日志文件路径未配置（filePath 为空）");
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return ToolResult.failure("日志文件不存在: " + filePath);
        }
        if (!Files.isReadable(path)) {
            return ToolResult.failure("日志文件不可读: " + filePath);
        }

        try {
            List<String> matched = new ArrayList<>();
            String kw = keyword != null ? keyword.toLowerCase() : "";
            int lineCount = 0;

            try (BufferedReader reader = new BufferedReader(
                    new FileReader(path.toFile(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && lineCount < MAX_LINES) {
                    lineCount++;
                    if (line.isBlank()) continue;
                    if (kw.isEmpty() || line.toLowerCase().contains(kw)) {
                        matched.add(line);
                        if (matched.size() >= limit) break;
                    }
                }
            }

            String header = String.format("[FILE] %s (关键词: %s, 匹配 %d 条, 已扫描 %d 行)\n\n",
                    filePath, keyword, matched.size(), lineCount);

            if (matched.isEmpty()) {
                return ToolResult.success(header + "未找到匹配关键词 \"" + keyword + "\" 的日志行");
            }

            return ToolResult.success(header + String.join("\n", matched));
        } catch (IOException e) {
            return ToolResult.failure("读取日志文件失败: " + e.getMessage());
        }
    }

    @Override
    public ToolResult test(LogSourceConfig config) {
        String filePath = config.property("filePath", "");
        if (filePath.isBlank()) {
            return ToolResult.failure("日志文件路径未配置（filePath 为空）");
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return ToolResult.failure("日志文件不存在: " + filePath);
        }
        if (!Files.isReadable(path)) {
            return ToolResult.failure("日志文件不可读: " + filePath);
        }

        try {
            long size = Files.size(path);
            return ToolResult.success("文件可读（" + filePath + "，" + size + " 字节）");
        } catch (IOException e) {
            return ToolResult.failure("读取文件信息失败: " + e.getMessage());
        }
    }

    @Override
    public LogSourceType supportedType() {
        return LogSourceType.FILE_PATH;
    }
}
