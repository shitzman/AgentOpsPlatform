package com.agentops.workflow;

import java.util.Map;

/**
 * Workflow 执行引擎 — 负责按序执行 {@link WorkflowDefinition} 中的步骤。
 *
 * <p>核心职责：
 * <ul>
 *   <li>按定义顺序依次调用每个步骤</li>
 *   <li>在步骤间传递 {@link WorkflowContext}</li>
 *   <li>处理步骤失败（默认遇错即停）</li>
 *   <li>返回最终上下文供调用方读取结果</li>
 * </ul>
 *
 * <p>V0.1 仅支持线性顺序执行。后续版本可扩展：
 * <ul>
 *   <li>条件分支（if/else）</li>
 *   <li>并行步骤</li>
 *   <li>重试策略</li>
 *   <li>步骤超时</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>{@code
 *   WorkflowDefinition diagnosis = new WorkflowDefinition("diagnosis", List.of(
 *       parseStackTrace, searchLogs, analyzeAndReport
 *   ));
 *
 *   WorkflowContext input = new SimpleWorkflowContext();
 *   input.put("rawStackTrace", userInput);
 *
 *   WorkflowContext result = engine.execute(diagnosis, input);
 *   String report = result.get("diagnosisReport", String.class);
 * }</pre>
 */
public interface WorkflowEngine {

    /**
     * 执行一个 Workflow 定义。
     *
     * @param definition    要执行的工作流定义
     * @param initialContext 初始上下文（包含工作流所需的输入数据）
     * @return 执行完成后的最终上下文（包含所有中间步骤和最终步骤的输出）
     * @throws WorkflowStepException 如果任一步骤执行失败
     */
    WorkflowContext execute(WorkflowDefinition definition, WorkflowContext initialContext);

    /**
     * 注册一个 Workflow 定义，后续可按名称执行。
     *
     * @param definition 工作流定义
     */
    void register(WorkflowDefinition definition);

    /**
     * 按名称执行已注册的 Workflow。
     *
     * @param name           已注册的工作流名称
     * @param initialContext 初始上下文
     * @return 最终上下文
     * @throws IllegalArgumentException 如果指定名称的 Workflow 未注册
     * @throws WorkflowStepException    如果任一步骤执行失败
     */
    WorkflowContext execute(String name, WorkflowContext initialContext);
}
