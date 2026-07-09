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

    /**
     * 测试日志源连通性 — 验证配置参数是否可用（保存前调用）。
     *
     * <p>默认实现返回成功，子类可覆盖以执行类型特定的检查：
     * <ul>
     *   <li>ELASTICSEARCH：HTTP GET {esUrl} 验证连通性和认证</li>
     *   <li>FILE_PATH：验证文件存在且可读</li>
     *   <li>TEXT_INPUT：验证 rawText 非空</li>
     * </ul>
     *
     * @param config 日志源配置
     * @return 成功时 output 包含确认信息（如 ES 版本号），失败时 error 包含原因
     */
    default ToolResult test(LogSourceConfig config) {
        return ToolResult.success("测试通过");
    }
}
