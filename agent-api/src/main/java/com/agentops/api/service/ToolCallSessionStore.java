package com.agentops.api.service;

import com.agentops.runtime.model.ChatMessage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具调用循环的内存态会话存储。
 *
 * <p>追问场景下，LLM 可能请求调用工具（返回 {@code tool_calls}）。由于工具执行需要用户批准
 * （人在回路），循环的中间消息（assistant 的 tool_calls 消息 + tool 结果消息）暂存在内存中，
 * 不写入 {@link ConversationService}（后者只存文本，无法保存 tool_calls）。
 *
 * <p>循环结束（LLM 返回最终文本）时，只把最终 user 文本和 assistant 文本存入
 * ConversationService，session 从本存储移除。
 *
 * <p>sessionId 与 conversationId 分离：同一会话可有多个并行循环；sessionId 为 UUID。
 * 中间态在应用重启后丢失（可接受 —— 用户重新追问即可）。
 */
@Service
public class ToolCallSessionStore {

    private final ConcurrentHashMap<String, ToolCallSession> sessions = new ConcurrentHashMap<>();

    /** 创建新的工具调用循环会话，返回 sessionId */
    public String create(String conversationId, String projectId, String traceId,
                         List<ChatMessage> messages) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ToolCallSession(
                conversationId, projectId, traceId,
                new ArrayList<>(messages), Instant.now()));
        return sessionId;
    }

    /** 获取会话（不存在返回 null） */
    public ToolCallSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    /** 更新会话的消息列表（替换为新的快照） */
    public void update(String sessionId, List<ChatMessage> messages) {
        ToolCallSession existing = sessions.get(sessionId);
        if (existing != null) {
            sessions.put(sessionId, new ToolCallSession(
                    existing.conversationId(), existing.projectId(), existing.traceId(),
                    new ArrayList<>(messages), existing.createdAt()));
        }
    }

    /** 移除会话（循环结束时调用） */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    /** 工具调用循环会话 — 持有完整的中间消息列表 */
    public record ToolCallSession(
            String conversationId,
            String projectId,
            String traceId,
            List<ChatMessage> messages,
            Instant createdAt) {
    }
}
