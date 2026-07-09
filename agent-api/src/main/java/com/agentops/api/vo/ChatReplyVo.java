package com.agentops.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * 追问响应 VO — POST /api/chat 和 POST /api/chat/continue 的返回结构。
 *
 * <p>纯文本回复：{@code {success:true, reply:"...", conversationId:"...", traceId:"...",
 *   executedToolResults:[...], toolRound:N, maxToolRounds:8}}。
 * <p>待批准工具调用：{@code {success:true, sessionId:"...", pendingToolCalls:[...],
 *   conversationId:"...", traceId:"...", executedToolResults:[...], toolRound:N, maxToolRounds:8}}。
 * <p>失败：{@code {success:false, error:"..."}}。
 *
 * @param success              是否成功
 * @param error                错误信息
 * @param reply                模型回复文本（工具调用场景为 null）
 * @param conversationId       会话 ID
 * @param traceId              OpenTelemetry Trace ID
 * @param sessionId            工具调用循环会话 ID（有待批准工具调用时返回）
 * @param pendingToolCalls     待批准的工具调用列表（LLM 请求调用的工具）
 * @param executedToolResults  本轮刚执行的工具结果列表（continue 场景返回，chat 首次为 null）
 * @param toolRound            当前工具调用轮次（1-based）
 * @param maxToolRounds        最大工具调用轮次（8）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatReplyVo(
        boolean success,
        String error,
        String reply,
        String conversationId,
        String traceId,
        String sessionId,
        List<PendingToolCallVo> pendingToolCalls,
        List<ToolExecutionResultVo> executedToolResults,
        Integer toolRound,
        Integer maxToolRounds) {

    public ChatReplyVo {
        pendingToolCalls = pendingToolCalls != null
                ? Collections.unmodifiableList(pendingToolCalls)
                : null;
        executedToolResults = executedToolResults != null
                ? Collections.unmodifiableList(executedToolResults)
                : null;
    }

    /** 纯文本回复（循环结束，可携带最后一轮的工具执行结果） */
    public static ChatReplyVo ok(String reply, String conversationId, String traceId,
                                 List<ToolExecutionResultVo> executedToolResults,
                                 Integer toolRound, Integer maxToolRounds) {
        return new ChatReplyVo(true, null, reply, conversationId, traceId, null, null,
                executedToolResults, toolRound, maxToolRounds);
    }

    /** 待批准工具调用（需用户批准后调 /api/chat/continue） */
    public static ChatReplyVo pending(String sessionId, List<PendingToolCallVo> pendingToolCalls,
                                      String conversationId, String traceId,
                                      List<ToolExecutionResultVo> executedToolResults,
                                      Integer toolRound, Integer maxToolRounds) {
        return new ChatReplyVo(true, null, null, conversationId, traceId, sessionId, pendingToolCalls,
                executedToolResults, toolRound, maxToolRounds);
    }

    public static ChatReplyVo error(String message) {
        return new ChatReplyVo(false, message, null, null, null, null, null, null, null, null);
    }
}
