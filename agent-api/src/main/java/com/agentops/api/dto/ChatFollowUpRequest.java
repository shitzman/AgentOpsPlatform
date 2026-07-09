package com.agentops.api.dto;

/**
 * 多轮追问请求 DTO — 在已有诊断基础上继续对话。
 *
 * <p>名称避让已存在的 {@code com.agentops.runtime.model.ChatRequest}。
 * 必填校验（conversationId、message）在 Service 层完成。
 *
 * @param conversationId 会话 ID（必填，由 /api/diagnosis 返回）
 * @param message        追问内容（必填）
 * @param projectId      项目 ID（可选，启用项目专属工具集）
 */
public record ChatFollowUpRequest(
        String conversationId,
        String message,
        String projectId) {
}
