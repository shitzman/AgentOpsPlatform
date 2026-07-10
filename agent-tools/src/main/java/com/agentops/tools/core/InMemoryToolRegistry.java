package com.agentops.tools.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ToolRegistry 的内存实现 — 基于 {@link ConcurrentHashMap} 的线程安全注册表。
 *
 * <p>适合开发环境和单机部署。生产环境可替换为支持持久化和集群同步的实现。
 */
public class InMemoryToolRegistry implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryToolRegistry.class);

    private final ConcurrentMap<String, ToolDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    @Override
    public void register(ToolDefinition definition, ToolExecutor executor) {
        if (definition == null) {
            throw new IllegalArgumentException("ToolDefinition 不能为 null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("ToolExecutor 不能为 null");
        }
        String name = definition.name();
        if (definitions.containsKey(name)) {
            throw new IllegalArgumentException("工具 [" + name + "] 已注册，不能重复注册");
        }
        definitions.put(name, definition);
        executors.put(name, executor);
        log.info("[register] tool registered: name={}, description={}",
                name, definition.description());
    }

    @Override
    public void unregister(String name) {
        definitions.remove(name);
        executors.remove(name);
        log.debug("[unregister] tool removed: name={}", name);
    }

    @Override
    public Optional<ToolDefinition> getDefinition(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    @Override
    public Optional<ToolExecutor> getExecutor(String name) {
        return Optional.ofNullable(executors.get(name));
    }

    @Override
    public List<ToolDefinition> listDefinitions() {
        return Collections.unmodifiableList(new ArrayList<>(definitions.values()));
    }
}
