package com.agentops.business.exceptionagent.model;

/**
 * 堆栈帧 — 异常堆栈中的单行信息。
 *
 * <p>从原始堆栈文本中解析提取，包含类名、方法名、文件名、行号，
 * 以及一个标识该帧是否属于本项目的标记。
 *
 * @param className     全限定类名（如 com.agentops.api.controller.OrderController）
 * @param methodName    方法名
 * @param fileName      源文件名（如 OrderController.java）
 * @param lineNumber    行号（无法解析时为 -1）
 * @param isProjectCode 是否为项目自身代码（用于过滤框架/第三方库的堆栈帧）
 */
public record StackTraceFrame(
        String className,
        String methodName,
        String fileName,
        int lineNumber,
        boolean isProjectCode) {

    public StackTraceFrame {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className 不能为空");
        }
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName 不能为空");
        }
    }

    /** 格式化输出，与标准 Java 堆栈格式一致 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\tat ").append(className).append('.').append(methodName);
        if (fileName != null) {
            sb.append('(').append(fileName);
            if (lineNumber >= 0) {
                sb.append(':').append(lineNumber);
            }
            sb.append(')');
        }
        return sb.toString();
    }
}
