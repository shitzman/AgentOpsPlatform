package com.agentops.tools;

import java.util.Map;

/**
 * 日志搜索提供者 — 定义日志源类型的查询策略。
 *
 * <p>每个实现负责将 {@link LogSourceConfig} 中的连接参数和 LLM 传入的
 * 搜索参数（keyword、timeRange 等）转换为实际的日志查询操作。
 *
 * <p>实现类必须是无状态的（线程安全），因为一个实现实例会被多个
 * 项目共享使用。
 *
 * <p>使用示例：
 * <pre>{@code
 *   LogProvider provider = new TextInputLogProvider();
 *   ToolResult result = provider.search(
 *       Map.of("keyword", "NullPointerException"),
 *       logSourceConfig);
 * }</pre>
 */
public interface LogProvider {

    /**
     * 执行日志搜索。
     *
     * @param args   LLM 传入的搜索参数（keyword、service、timeRange、limit）
     * @param config 日志源配置（连接参数和类型）
     * @return 搜索结果（成功时 output 包含日志行，失败时 error 包含原因）
     */
    ToolResult search(Map<String, Object> args, LogSourceConfig config);

    /**
     * 返回此提供者支持的日志源类型。
     * 用于在 {@code LogProviderRegistry} 中注册和查找。
     */
    LogSourceType supportedType();
}
