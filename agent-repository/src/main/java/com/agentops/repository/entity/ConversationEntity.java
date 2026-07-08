package com.agentops.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 对话实体 — 映射 conversations 表。
 *
 * <p>每条记录代表一次对话中的一条消息（user / assistant / system）。
 * 同一 conversationId 的消息按 created_at ASC 排序即可还原完整对话历史。
 */
@TableName("conversations")
public class ConversationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String projectId;
    /** 角色：system / user / assistant */
    private String role;
    /** 消息内容（JSON 序列化的 ChatMessage 或纯文本） */
    private String content;
    private LocalDateTime createdAt;

    public ConversationEntity() {}

    public ConversationEntity(Long id, String conversationId, String projectId,
                              String role, String content, LocalDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.projectId = projectId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    // ---- getters ----

    public Long getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getProjectId() { return projectId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ---- setters ----

    public void setId(Long id) { this.id = id; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public void setRole(String role) { this.role = role; }
    public void setContent(String content) { this.content = content; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
