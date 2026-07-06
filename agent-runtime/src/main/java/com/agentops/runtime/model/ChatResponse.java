package com.agentops.runtime.model;

import java.util.Collections;
import java.util.List;

/**
 * 对话响应 — LLM 调用的返回结果。
 *
 * <p>模型可能返回纯文本（finishReason="stop"），也可能返回工具调用请求
 * （finishReason="tool_calls"）。调用方需要根据 finishReason 决定
 * 下一步动作：展示文本给用户，还是执行工具并将结果回传。
 *
 * @param content     模型的文本回复（工具调用场景下为 null）
 * @param toolCalls   模型请求的工具调用列表（纯文本回复场景下为空）
 * @param finishReason 结束原因：stop / tool_calls / length / content_filter
 */
public record ChatResponse(
        String content,
        List<ToolCall> toolCalls,
        String finishReason) {

    public ChatResponse {
        if (finishReason == null || finishReason.isBlank()) {
            throw new IllegalArgumentException("finishReason 不能为空");
        }
        toolCalls = toolCalls != null
                ? Collections.unmodifiableList(toolCalls)
                : Collections.emptyList();
    }

    /** 是否请求了工具调用 */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /** 是否为正常的文本回复结束 */
    public boolean isFinished() {
        return "stop".equals(finishReason);
    }
}
