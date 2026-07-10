package com.agentops.tools.log;

import java.util.Optional;

/**
 * 日志提供者注册表 — 按 {@link LogSourceType} 管理 {@link LogProvider} 实现。
 *
 * <p>模式与 {@link ToolRegistry} 一致：注册表持有所有可用的提供者，
 * 使用时按类型查找对应的实现。
 *
 * <p>在 {@code AgentOpsConfig} 中注册所有 LogProvider 实现。
 */
public interface LogProviderRegistry {

    /** 注册一个日志提供者（相同类型会覆盖旧实现） */
    void register(LogProvider provider);

    /** 按日志源类型查找对应的提供者 */
    Optional<LogProvider> get(LogSourceType type);

    /** 返回已注册的提供者数量（用于健康检查） */
    int size();
}
