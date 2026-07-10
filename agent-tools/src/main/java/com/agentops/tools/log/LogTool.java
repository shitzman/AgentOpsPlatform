package com.agentops.tools.log;

import com.agentops.tools.core.ToolDefinition;
import com.agentops.tools.core.ToolExecutor;
import com.agentops.tools.core.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * 日志查询工具 — 按关键词和时间范围搜索日志。
 *
 * <p>V0.5 支持可插拔的日志源后端，通过 {@link LogProvider} 接口
 * 适配不同的日志存储系统：
 * <ul>
 *   <li>{@link TextInputLogProvider} — UI 中粘贴的原始文本</li>
 *   <li>{@link FileLogProvider} — 服务器本地日志文件</li>
 *   <li>{@link ElasticsearchLogProvider} — ES 日志存储（模拟）</li>
 * </ul>
 *
 * <p>两种构造器：
 * <ul>
 *   <li>无参 — 返回模拟数据（向后兼容，全局模式）</li>
 *   <li>带 {@code LogProvider + LogSourceConfig} — 委托给真实日志源（项目模式）</li>
 * </ul>
 */
public class LogTool {

    private final LogProvider provider;
    private final LogSourceConfig config;

    /** 无参构造器（向后兼容 — 返回模拟日志数据） */
    public LogTool() {
        this.provider = null;
        this.config = null;
    }

    /** 项目级构造器 — 绑定指定日志源 */
    public LogTool(LogProvider provider, LogSourceConfig config) {
        this.provider = provider;
        this.config = config;
    }

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

    /** log-search 执行器 — 有 provider 时委托，否则返回模拟数据 */
    public ToolExecutor executor() {
        // 项目模式：委托给真实的日志提供者
        if (provider != null && config != null) {
            return args -> provider.search(args, config);
        }

        // 全局模式：返回模拟数据（向后兼容）
        return args -> {
            String keyword = (String) args.get("keyword");
            String service = (String) args.getOrDefault("service", "unknown");
            int limit = args.containsKey("limit")
                    ? ((Number) args.get("limit")).intValue() : 10;

            String result = String.format("""
                    [%s] 日志搜索结果 (关键词: %s, 最多 %d 条)

                    --- 模拟日志（对接真实日志系统后替换） ---
                    [2026-07-06 17:30:01] ERROR [%s] %s - 异常详情
                    [2026-07-06 17:29:58] WARN  [%s] 请求超时 - 耗时 5200ms
                    [2026-07-06 17:29:55] INFO  [%s] 请求开始 - GET /api/orders
                    --- 共 3 条模拟日志 ---

                    提示：当前为全局模式的模拟日志。
                    请在"项目配置"中为项目添加日志源以使用真实查询。
                    """,
                    service, keyword, limit,
                    service, keyword,
                    service,
                    service);

            return ToolResult.success(result);
        };
    }
}
