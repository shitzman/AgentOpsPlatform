package com.agentops.prompts;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PromptRegistry 的内存实现 — 基于 {@link ConcurrentHashMap} 的线程安全注册表。
 *
 * <p>不负责 Prompt 文件的加载（加载由外部调用 {@link #register(PromptTemplate)} 完成）。
 * 通常配合 Spring 的 {@code @PostConstruct} 或配置类在启动时批量注册。
 */
public class InMemoryPromptRegistry implements PromptRegistry {

    private final Map<String, PromptTemplate> templates = new ConcurrentHashMap<>();

    @Override
    public void register(PromptTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("PromptTemplate 不能为 null");
        }
        if (templates.containsKey(template.name())) {
            throw new IllegalArgumentException("Prompt [" + template.name() + "] 已注册，不能重复注册");
        }
        templates.put(template.name(), template);
    }

    @Override
    public void unregister(String name) {
        templates.remove(name);
    }

    @Override
    public Optional<PromptTemplate> get(String name) {
        return Optional.ofNullable(templates.get(name));
    }

    @Override
    public String render(String name, Map<String, Object> variables) {
        PromptTemplate template = get(name)
                .orElseThrow(() -> new IllegalArgumentException("未注册的 Prompt 模板: " + name));
        return template.render(variables);
    }

    @Override
    public Set<String> listNames() {
        return Collections.unmodifiableSet(templates.keySet());
    }
}
