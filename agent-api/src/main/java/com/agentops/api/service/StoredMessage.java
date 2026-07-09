package com.agentops.api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 持久化对话消息 — 替换旧 {@code Map<String,String>} 序列化载体。
 *
 * <p>JSON 形态与旧实现一致：{@code {"role":"user","content":"..."}}。
 * {@link JsonIgnoreProperties} 兼容历史可能含 {@code toolCallId} 等额外字段的记录。
 *
 * @param role    消息角色（system / user / assistant / tool）
 * @param content 文本内容
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredMessage(String role, String content) {

    public StoredMessage {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("消息角色不能为空");
        }
        content = content != null ? content : "";
    }
}
