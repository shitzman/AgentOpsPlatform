package com.agentops.workflow;

/**
 * Workflow 中的单个执行步骤。
 *
 * <p>每个步骤接收当前上下文，执行业务逻辑，然后将结果写回上下文。
 * 这是函数式接口，实现者可以用 Lambda 或方法引用。
 *
 * <p>步骤应遵循单一职责原则：一个步骤只做一件事（如"解析堆栈"、
 * "查询日志"、"生成报告"），通过上下文与前后步骤通信。
 *
 * <p>典型用法：
 * <pre>{@code
 *   WorkflowStep parseTrace = ctx -> {
 *       String raw = ctx.get("rawStackTrace", String.class);
 *       StackTrace parsed = parser.parse(raw);
 *       ctx.put("parsedTrace", parsed);
 *       return ctx;
 *   };
 * }</pre>
 */
@FunctionalInterface
public interface WorkflowStep {

    /**
     * 执行当前步骤。
     *
     * @param context 当前 Workflow 上下文（包含上游步骤的所有数据）
     * @return 更新后的上下文（通常就是传入的同一个 context 实例）
     * @throws WorkflowStepException 如果步骤执行失败
     */
    WorkflowContext execute(WorkflowContext context);
}
