package com.agentops.api.dto;

/**
 * 诊断请求 DTO — 提交异常堆栈或日志内容进行诊断。
 *
 * <p>支持两种输入模式：
 * <ul>
 *   <li>堆栈诊断：提供 {@code stackTrace}</li>
 *   <li>纯日志分析：仅提供 {@code logContent}（系统自动尝试提取堆栈）</li>
 * </ul>
 *
 * <p>必填校验在 Service 层完成（至少提供 stackTrace 或 logContent 之一），
 * 避免在 record 构造器中抛异常导致 Jackson 返回 Spring 默认 400 格式。
 *
 * @param stackTrace     原始 Java 异常堆栈文本（可选）
 * @param logContent     原始日志文本（可选，纯日志模式时为主要输入）
 * @param conversationId 会话 ID（可选，用于多轮对话）
 * @param projectId      项目 ID（可选，启用多源上下文）
 */
public record DiagnosisRequest(
        String stackTrace,
        String logContent,
        String conversationId,
        String projectId) {
}
