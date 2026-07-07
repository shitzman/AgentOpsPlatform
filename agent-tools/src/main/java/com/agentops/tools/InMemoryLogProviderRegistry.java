package com.agentops.tools;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版日志提供者注册表 — 线程安全的 {@link LogProviderRegistry} 实现。
 *
 * <p>开发环境使用，生产环境可替换为支持动态热加载的实现。
 */
public class InMemoryLogProviderRegistry implements LogProviderRegistry {

    private final Map<LogSourceType, LogProvider> providers = new ConcurrentHashMap<>();

    @Override
    public void register(LogProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider 不能为 null");
        }
        providers.put(provider.supportedType(), provider);
    }

    @Override
    public Optional<LogProvider> get(LogSourceType type) {
        return Optional.ofNullable(providers.get(type));
    }

    @Override
    public int size() {
        return providers.size();
    }
}
