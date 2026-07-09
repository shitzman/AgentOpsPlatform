package com.agentops.api.service;

import com.agentops.memory.MemoryEntry;
import com.agentops.memory.MemoryStore;
import com.agentops.runtime.model.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 对话历史服务 — 负责多轮对话的加载与持久化。
 *
 * <p>从 {@code DiagnosisController} 抽取，使用 {@link StoredMessage} record 替代旧的
 * {@code Map<String,String>} 进行序列化，JSON 形态保持不变。
 */
@Service
public class ConversationService {

    private static final int HISTORY_LIMIT = 20;

    private final MemoryStore memoryStore;
    private final ObjectMapper objectMapper;

    public ConversationService(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 加载指定会话的历史消息到目标列表（按时间正序追加）。
     *
     * @param conversationId 会话 ID
     * @param target         待追加的消息列表
     */
    public void loadHistory(String conversationId, List<ChatMessage> target) {
        List<MemoryEntry> history = memoryStore.findByType(typeOf(conversationId), HISTORY_LIMIT);
        // MemoryStore 按创建时间倒序返回，倒序遍历恢复正序
        for (int i = history.size() - 1; i >= 0; i--) {
            deserialize(history.get(i)).ifPresent(target::add);
        }
    }

    /**
     * 保存一次对话的系统消息（仅首次）、最后一条用户消息和助手回复。
     *
     * @param conversationId  会话 ID（为空时自动生成）
     * @param requestMessages 本次请求的消息列表（用于提取 system 和最后一条 user）
     * @param responseMessage 助手回复消息
     * @return 实际使用的会话 ID
     */
    public String save(String conversationId,
                       List<ChatMessage> requestMessages,
                       ChatMessage responseMessage) {
        String cid = resolveConversationId(conversationId);
        String type = typeOf(cid);

        try {
            saveSystemOnce(type, requestMessages);
            saveLastUser(type, requestMessages);
            saveAssistant(type, responseMessage);
        } catch (Exception ignored) {
            // 持久化失败不影响主流程
        }
        return cid;
    }

    // ---- 私有辅助 ----

    private static String typeOf(String conversationId) {
        return "conversation:" + conversationId;
    }

    private static String resolveConversationId(String conversationId) {
        return (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();
    }

    /** 系统消息仅在该会话首次出现时保存一次 */
    private void saveSystemOnce(String type, List<ChatMessage> requestMessages) {
        if (requestMessages.size() <= 1) return;
        ChatMessage first = requestMessages.get(0);
        if (!"system".equals(first.role()) || first.content() == null) return;
        if (!memoryStore.findByType(type, 1).isEmpty()) return;
        persist(type, new StoredMessage("system", first.content()));
    }

    /** 保存请求消息中最后一条 user 消息 */
    private void saveLastUser(String type, List<ChatMessage> requestMessages) {
        for (int i = requestMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = requestMessages.get(i);
            if ("user".equals(msg.role()) && msg.content() != null) {
                persist(type, new StoredMessage("user", msg.content()));
                return;
            }
        }
    }

    private void saveAssistant(String type, ChatMessage responseMessage) {
        if (responseMessage.content() == null) return;
        persist(type, new StoredMessage("assistant", responseMessage.content()));
    }

    private void persist(String type, StoredMessage message) {
        try {
            memoryStore.save(MemoryEntry.pending(type, objectMapper.writeValueAsString(message)));
        } catch (JsonProcessingException ignored) {
            // 序列化失败跳过该条
        }
    }

    private java.util.Optional<ChatMessage> deserialize(MemoryEntry entry) {
        try {
            StoredMessage msg = objectMapper.readValue(entry.content(), StoredMessage.class);
            return java.util.Optional.of(new ChatMessage(msg.role(), msg.content(), null, List.of()));
        } catch (JsonProcessingException ignored) {
            return java.util.Optional.empty();
        }
    }
}
