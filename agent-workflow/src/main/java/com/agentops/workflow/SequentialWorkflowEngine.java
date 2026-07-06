package com.agentops.workflow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 顺序 Workflow 执行引擎 — {@link WorkflowEngine} 的默认实现。
 *
 * <p>按 {@link WorkflowDefinition#steps()} 的顺序依次执行每个步骤。
 * 前一步的输出上下文作为后一步的输入。任一步骤失败则立即终止。
 *
 * <p>线程安全：注册/执行操作使用 {@link ConcurrentHashMap} 保证并发安全。
 */
public class SequentialWorkflowEngine implements WorkflowEngine {

    private final Map<String, WorkflowDefinition> definitions = new ConcurrentHashMap<>();

    @Override
    public void register(WorkflowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Workflow 定义不能为 null");
        }
        definitions.put(definition.name(), definition);
    }

    @Override
    public WorkflowContext execute(WorkflowDefinition definition, WorkflowContext initialContext) {
        if (definition == null) {
            throw new IllegalArgumentException("Workflow 定义不能为 null");
        }
        WorkflowContext context = initialContext != null ? initialContext : new SimpleWorkflowContext();

        for (WorkflowStep step : definition.steps()) {
            try {
                context = step.execute(context);
            } catch (WorkflowStepException e) {
                throw e;
            } catch (Exception e) {
                throw new WorkflowStepException(
                        "unknown", "步骤执行异常: " + e.getMessage(), e);
            }
        }
        return context;
    }

    @Override
    public WorkflowContext execute(String name, WorkflowContext initialContext) {
        WorkflowDefinition definition = definitions.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("未注册的 Workflow: " + name);
        }
        return execute(definition, initialContext);
    }
}
