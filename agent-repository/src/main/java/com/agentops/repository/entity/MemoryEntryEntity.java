package com.agentops.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Memory Entry 实体 — 映射 memory_entries 表。
 *
 * <p>通用记忆存储，支持按 type 分类和全文搜索。
 * MySqlMemoryStore 使用此实体实现 MemoryStore 接口。
 */
@TableName("memory_entries")
public class MemoryEntryEntity {

    @TableId
    private String id;
    private String type;
    private String content;
    /** JSON 格式元数据 */
    private String metadata;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public MemoryEntryEntity() {}

    public MemoryEntryEntity(String id, String type, String content,
                             String metadata, LocalDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.content = content;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    // ---- getters ----

    public String getId() { return id; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public String getMetadata() { return metadata; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ---- setters ----

    public void setId(String id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setContent(String content) { this.content = content; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
