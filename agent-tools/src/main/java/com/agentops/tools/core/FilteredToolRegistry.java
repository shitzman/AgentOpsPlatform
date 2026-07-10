package com.agentops.tools.core;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 工具注册表装饰器 — 按启用工具名称集合过滤。
 *
 * <p>包装一个全局 {@link ToolRegistry}，只暴露名称在
 * {@code enabledNames} 集合中的工具。用于实现项目级别的
 * 工具选择功能。
 *
 * <p>此装饰器不修改底层注册表，对全局注册表的所有变更
 * 会直接反映到过滤视图中。
 *
 * <p>使用示例：
 * <pre>{@code
 *   ToolRegistry global = ...;
 *   Set<String> enabled = Set.of("git-log", "git-blame");
 *   ToolRegistry filtered = new FilteredToolRegistry(global, enabled);
 *   // filtered.listDefinitions() 只返回 git-log 和 git-blame
 * }</pre>
 */
public class FilteredToolRegistry implements ToolRegistry {

    private final ToolRegistry delegate;
    private final Set<String> enabledNames;

    /**
     * @param delegate     底层全局工具注册表
     * @param enabledNames 允许暴露的工具名称集合
     */
    public FilteredToolRegistry(ToolRegistry delegate, Set<String> enabledNames) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 不能为 null");
        }
        this.delegate = delegate;
        this.enabledNames = Set.copyOf(enabledNames);
    }

    @Override
    public void register(ToolDefinition definition, ToolExecutor executor) {
        delegate.register(definition, executor);
    }

    @Override
    public void unregister(String name) {
        delegate.unregister(name);
    }

    @Override
    public Optional<ToolDefinition> getDefinition(String name) {
        if (!enabledNames.contains(name)) {
            return Optional.empty();
        }
        return delegate.getDefinition(name);
    }

    @Override
    public Optional<ToolExecutor> getExecutor(String name) {
        if (!enabledNames.contains(name)) {
            return Optional.empty();
        }
        return delegate.getExecutor(name);
    }

    @Override
    public List<ToolDefinition> listDefinitions() {
        return delegate.listDefinitions().stream()
                .filter(td -> enabledNames.contains(td.name()))
                .toList();
    }

    /** 返回当前启用的工具名称集合（用于调试和 UI 展示） */
    public Set<String> enabledToolNames() {
        return enabledNames;
    }
}
