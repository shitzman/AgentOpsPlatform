package com.agentops.runtime.model;

import java.util.Collections;
import java.util.List;

/**
 * 对话消息 — 表示 LLM 对话中的单条消息。
 *
 * <p>覆盖四种角色：
 * <ul>
 *   <li>{@code system} — 系统级 Prompt，设定 Agent 的行为边界</li>
 *   <li>{@code user} — 用户输入，如提交的异常堆栈</li>
 *   <li>{@code assistant} — 模型回复，可能包含文本或工具调用请求</li>
 *   <li>{@code tool} — 工具执行结果，回传给模型继续推理</li>
 * </ul>
 *
 * @param role       消息角色（system / user / assistant / tool）
 * @param content    文本内容（assistant 在 tool_calls 场景下可为 null）
 * @param toolCallId 工具调用 ID（仅 tool 角色使用，将工具结果关联到调用）
 * @param toolCalls  工具调用请求列表（仅 assistant 角色使用）
 */
public record ChatMessage(
        String role,
        String content,
        String toolCallId,
        List<ToolCall> toolCalls) {

    public ChatMessage {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("消息角色不能为空");
        }
        if (content == null && toolCalls == null) {
            throw new IllegalArgumentException("content 和 toolCalls 不能同时为 null");
        }
        toolCalls = toolCalls != null
                ? Collections.unmodifiableList(toolCalls)
                : Collections.emptyList();
    }

    // ---- 工厂方法 ----

    /** 创建 system 消息 */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    /** 创建 user 消息 */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    /** 创建 assistant 文本回复消息 */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    /** 创建 assistant 工具调用消息 */
    public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", null, null, toolCalls);
    }

    /** 创建 tool 执行结果消息 */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null);
    }
}
