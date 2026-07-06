package com.agentops.workflow;

/**
 * Workflow 步骤执行失败时抛出的异常。
 *
 * <p>包含步骤名称和失败原因，方便上层 Workflow 引擎决定是终止还是跳过。
 */
public class WorkflowStepException extends RuntimeException {

    private final String stepName;

    /**
     * @param stepName 失败的步骤名称
     * @param message  错误描述
     * @param cause    原始异常
     */
    public WorkflowStepException(String stepName, String message, Throwable cause) {
        super("步骤 [" + stepName + "] 执行失败: " + message, cause);
        this.stepName = stepName;
    }

    /**
     * @return 失败的步骤名称
     */
    public String getStepName() {
        return stepName;
    }
}
