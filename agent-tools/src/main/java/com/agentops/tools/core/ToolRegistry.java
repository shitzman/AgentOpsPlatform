package com.agentops.tools.core;

import java.util.List;
import java.util.Optional;

/**
 * 工具注册中心 — 领域 Agent 访问所有外部能力的统一入口。
 *
 * <p><b>核心约束（不可违反）：</b>
 * 所有外部能力必须表示为工具，通过本注册中心访问。领域 Agent 不得绕过
 * 注册中心直接访问数据库、Git 仓库、日志、指标或任何外部系统。
 *
 * <p>在 Agent 推理循环中的典型用法：
 * <pre>{@code
 *   // 1. 查询可用工具列表，传递给 LLM
 *   List<ToolDefinition> available = registry.listDefinitions();
 *
 *   // 2. LLM 选择工具后，从注册中心查找并执行
 *   ToolExecutor executor = registry.getExecutor("log-search")
 *       .orElseThrow(() -> new IllegalArgumentException("未知工具: log-search"));
 *   ToolResult result = executor.execute(Map.of("keyword", "NullPointerException"));
 * }</pre>
 */
public interface ToolRegistry {

    /**
     * 注册一个工具定义及其执行器。
     *
     * @param definition 工具元数据
     * @param executor   工具执行逻辑
     * @throws IllegalArgumentException 如果同名工具已存在
     */
    void register(ToolDefinition definition, ToolExecutor executor);

    /**
     * 从注册中心移除一个工具。
     *
     * @param name 工具名称
     */
    void unregister(String name);

    /**
     * 按名称查找工具定义。
     *
     * @param name 工具名称
     * @return 工具定义，不存在时返回 {@link Optional#empty()}
     */
    Optional<ToolDefinition> getDefinition(String name);

    /**
     * 按名称查找工具执行器。
     *
     * @param name 工具名称
     * @return 工具执行器，不存在时返回 {@link Optional#empty()}
     */
    Optional<ToolExecutor> getExecutor(String name);

    /**
     * 返回所有已注册的工具定义列表。
     *
     * @return 不可变列表
     */
    List<ToolDefinition> listDefinitions();
}
