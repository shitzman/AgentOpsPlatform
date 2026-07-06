package com.agentops.workflow;

import java.util.List;

/**
 * Workflow 定义 — 包含名称和有序步骤序列。
 *
 * <p>Definition 是纯数据对象，不包含执行逻辑。执行由 {@link WorkflowEngine} 负责。
 * 同一个 Definition 可以被多次执行，每次使用不同的初始上下文。
 *
 * <p>步骤按列表顺序依次执行，前一步的输出上下文作为后一步的输入。
 *
 * @param name  工作流名称，全局唯一
 * @param steps 有序的执行步骤列表
 */
public record WorkflowDefinition(String name, List<WorkflowStep> steps) {

    public WorkflowDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工作流名称不能为空");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("工作流至少需要一个步骤");
        }
        // 防御性拷贝，防止外部修改
        steps = List.copyOf(steps);
    }
}
