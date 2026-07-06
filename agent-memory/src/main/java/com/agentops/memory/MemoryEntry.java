package com.agentops.memory;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Memory 条目 — Agent 执行过程中产生或需要持久化的单条记录。
 *
 * <p>每条记录包含：
 * <ul>
 *   <li>{@code id} — 全局唯一标识，由存储层生成或调用方指定</li>
 *   <li>{@code type} — 分类标签（如 "conversation"、"diagnosis"、"tool_result"）</li>
 *   <li>{@code content} — 正文内容，可以是文本、JSON 或任意格式</li>
 *   <li>{@code metadata} — 附加元数据（如来源、关联异常 ID、优先级等）</li>
 *   <li>{@code createdAt} — 创建时间</li>
 * </ul>
 *
 * <p>这是不可变数据对象，通过 {@link MemoryStore} 进行持久化。
 *
 * @param id       唯一标识
 * @param type     记忆类型（分类标签）
 * @param content  记忆内容
 * @param metadata 附加元数据
 * @param createdAt 创建时间
 */
public record MemoryEntry(
        String id,
        String type,
        String content,
        Map<String, String> metadata,
        Instant createdAt) {

    public MemoryEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Memory ID 不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Memory type 不能为空");
        }
        if (content == null) {
            throw new IllegalArgumentException("Memory content 不能为 null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt 不能为 null");
        }
        // 防御性拷贝
        metadata = metadata != null
                ? Collections.unmodifiableMap(new HashMap<>(metadata))
                : Collections.emptyMap();
    }

    /**
     * 创建新的 MemoryEntry，自动设置当前时间为创建时间。
     *
     * @param id      唯一标识
     * @param type    记忆类型
     * @param content 记忆内容
     * @return 新的 MemoryEntry
     */
    public static MemoryEntry now(String id, String type, String content) {
        return new MemoryEntry(id, type, content, Collections.emptyMap(), Instant.now());
    }

    /**
     * 创建一个新的 MemoryEntry 但不设置 id（由存储层生成）。
     *
     * @param type    记忆类型
     * @param content 记忆内容
     * @return 新的 MemoryEntry（id 为 placeholder）
     */
    public static MemoryEntry pending(String type, String content) {
        return new MemoryEntry("PENDING", type, content, Collections.emptyMap(), Instant.now());
    }
}
