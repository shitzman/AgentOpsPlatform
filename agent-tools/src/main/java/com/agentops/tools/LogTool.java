package com.agentops.tools;

import java.util.List;
import java.util.Map;

/**
 * 日志查询工具 — 按关键词和时间范围搜索日志。
 *
 * <p>V0.4 提供模拟实现（返回示例日志数据），后续可对接真实日志系统：
 * <ul>
 *   <li>Elasticsearch / OpenSearch</li>
 *   <li>Grafana Loki</li>
 *   <li>阿里云 SLS</li>
 *   <li>本地日志文件</li>
 * </ul>
 */
public class LogTool {

    /** log-search 工具定义 */
    public static ToolDefinition definition() {
        return new ToolDefinition("log-search",
                "按关键词和时间范围搜索应用日志",
                Map.of("type", "object",
                        "properties", Map.of(
                                "keyword", Map.of("type", "string",
                                        "description", "日志搜索关键词（支持模糊匹配）"),
                                "service", Map.of("type", "string",
                                        "description", "服务名称，如 order-service"),
                                "timeRange", Map.of("type", "string",
                                        "description", "时间范围，如 5m / 1h / 24h"),
                                "limit", Map.of("type", "integer",
                                        "description", "最多返回条数，默认 10")
                        ),
                        "required", List.of("keyword")));
    }

    /** log-search 执行器（V0.4 模拟实现） */
    public ToolExecutor executor() {
        return args -> {
            String keyword = (String) args.get("keyword");
            String service = (String) args.getOrDefault("service", "unknown");
            int limit = args.containsKey("limit")
                    ? ((Number) args.get("limit")).intValue() : 10;

            // V0.4 模拟日志数据
            String result = String.format("""
                    [%s] 日志搜索结果 (关键词: %s, 最多 %d 条)

                    --- 模拟日志（对接真实日志系统后替换） ---
                    [2026-07-06 17:30:01] ERROR [%s] %s - 异常详情
                    [2026-07-06 17:29:58] WARN  [%s] 请求超时 - 耗时 5200ms
                    [2026-07-06 17:29:55] INFO  [%s] 请求开始 - GET /api/orders
                    --- 共 3 条模拟日志 ---

                    提示：当前为模拟日志实现。
                    生产环境请将 LogTool 对接 ELK / Loki / SLS 等日志系统。
                    """,
                    service, keyword, limit,
                    service, keyword,
                    service,
                    service);

            return ToolResult.success(result);
        };
    }
}
