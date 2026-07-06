package com.agentops.prompts;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Prompt 注册中心 — 管理所有 Prompt 模板的注册、查找与渲染。
 *
 * <p>核心职责：
 * <ul>
 *   <li>存储和管理命名 Prompt 模板</li>
 *   <li>支持按名称查找模板</li>
 *   <li>提供一步到位的"查找 + 渲染"便捷方法</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>Prompt 模板应存储在 classpath 资源文件中，而非硬编码在 Java 代码中</li>
 *   <li>运行时通过 {@link #register(PromptTemplate)} 加载到注册中心</li>
 *   <li>领域 Agent 通过 {@link #render(String, Map)} 获取渲染后的 Prompt</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>{@code
 *   // 启动时加载
 *   registry.register(new PromptTemplate("diagnosis", resourceLoader.load("prompts/diagnosis.txt")));
 *
 *   // 运行时渲染
 *   String prompt = registry.render("diagnosis", Map.of("stackTrace", trace));
 * }</pre>
 */
public interface PromptRegistry {

    /**
     * 注册一个 Prompt 模板。
     *
     * @param template Prompt 模板
     * @throws IllegalArgumentException 如果同名模板已存在
     */
    void register(PromptTemplate template);

    /**
     * 移除指定名称的 Prompt 模板。
     *
     * @param name 模板名称
     */
    void unregister(String name);

    /**
     * 按名称查找 Prompt 模板。
     *
     * @param name 模板名称
     * @return 模板对象，不存在时返回 {@link Optional#empty()}
     */
    Optional<PromptTemplate> get(String name);

    /**
     * 按名称查找模板并渲染，是将 {@link #get(String)} 与 {@link PromptTemplate#render(Map)} 合并的便捷方法。
     *
     * @param name      模板名称
     * @param variables 渲染变量
     * @return 渲染后的 Prompt 文本
     * @throws IllegalArgumentException 如果模板不存在，或占位符变量缺失
     */
    String render(String name, Map<String, Object> variables);

    /**
     * 返回所有已注册的模板名称。
     *
     * @return 不可变集合
     */
    Set<String> listNames();
}
