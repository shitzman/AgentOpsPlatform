package com.agentops.tools.log;

import com.agentops.tools.core.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本输入日志提供者 — 在 {@link LogSourceConfig#properties()} 中的
 * {@code rawText} 字段上执行关键词搜索。
 *
 * <p>适用于用户在 Web UI 中直接粘贴日志文本进行快速测试的场景。
 * 搜索逻辑为简单的按行包含匹配（不区分大小写）。
 */
public class TextInputLogProvider implements LogProvider {

    @Override
    public ToolResult search(Map<String, Object> args, LogSourceConfig config) {
        String keyword = (String) args.get("keyword");
        String rawText = config.property("rawText", "");
        int limit = args.containsKey("limit")
                ? ((Number) args.get("limit")).intValue() : 10;

        if (rawText.isBlank()) {
            return ToolResult.failure("文本输入日志源中没有内容（rawText 为空）");
        }

        String[] lines = rawText.split("\\r?\\n");
        List<String> matched = new ArrayList<>();
        String kw = keyword != null ? keyword.toLowerCase() : "";

        for (String line : lines) {
            if (line.isBlank()) continue;
            if (kw.isEmpty() || line.toLowerCase().contains(kw)) {
                matched.add(line);
                if (matched.size() >= limit) break;
            }
        }

        if (matched.isEmpty()) {
            return ToolResult.success("未找到匹配关键词 \"" + keyword + "\" 的日志行");
        }

        String header = String.format("[TEXT_INPUT] 日志搜索结果 (关键词: %s, 匹配 %d 条)\n\n", keyword, matched.size());
        return ToolResult.success(header + String.join("\n", matched));
    }

    @Override
    public ToolResult test(LogSourceConfig config) {
        String rawText = config.property("rawText", "");
        if (rawText.isBlank()) {
            return ToolResult.failure("日志文本为空（rawText 未配置）");
        }
        int lineCount = rawText.split("\\r?\\n").length;
        return ToolResult.success("文本日志源可用（" + lineCount + " 行，" + rawText.length() + " 字符）");
    }

    @Override
    public LogSourceType supportedType() {
        return LogSourceType.TEXT_INPUT;
    }
}
