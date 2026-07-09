package com.agentops.api.dto;

import java.util.Collections;
import java.util.List;

/**
 * 工具批准请求 DTO — 用户批准/修改/拒绝 LLM 建议的工具调用后，提交执行。
 *
 * @param sessionId        工具调用循环的会话 ID（由 /api/chat 返回）
 * @param approvedToolCalls 用户批准执行的工具调用列表（含可能修改的参数）
 */
public record ToolApproveRequest(String sessionId, List<ApprovedToolCall> approvedToolCalls) {

    public ToolApproveRequest {
        approvedToolCalls = approvedToolCalls != null
                ? Collections.unmodifiableList(approvedToolCalls)
                : Collections.emptyList();
    }

    /** 单个被批准的工具调用（参数可被用户修改） */
    public record ApprovedToolCall(String id, String name, String arguments) {
    }
}
