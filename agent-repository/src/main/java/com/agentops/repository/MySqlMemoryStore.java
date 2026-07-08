package com.agentops.repository;

import com.agentops.memory.MemoryEntry;
import com.agentops.memory.MemoryStore;
import com.agentops.repository.entity.MemoryEntryEntity;
import com.agentops.repository.mapper.MemoryEntryMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * MemoryStore 的 MySQL 实现 — 基于 MyBatis-Plus + memory_entries 表。
 *
 * <p>替代 InMemoryMemoryStore，所有数据持久化到 MySQL，应用重启不丢失。
 *
 * <p>线程安全由 MyBatis-Plus 和 MySQL 的数据库事务保证。
 */
public class MySqlMemoryStore implements MemoryStore {

    private final MemoryEntryMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MySqlMemoryStore(MemoryEntryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MemoryEntry save(MemoryEntry entry) {
        MemoryEntryEntity entity = toEntity(entry);
        MemoryEntryEntity existing = mapper.selectById(entry.id());
        if (existing != null) {
            mapper.updateById(entity);
        } else {
            mapper.insert(entity);
        }
        return entry;
    }

    @Override
    public Optional<MemoryEntry> findById(String id) {
        MemoryEntryEntity entity = mapper.selectById(id);
        return Optional.ofNullable(entity).map(this::fromEntity);
    }

    @Override
    public List<MemoryEntry> findByType(String type, int limit) {
        QueryWrapper<MemoryEntryEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type)
               .orderByDesc("created_at")
               .last("LIMIT " + Math.min(limit, 1000));
        return mapper.selectList(wrapper).stream()
                .map(this::fromEntity)
                .toList();
    }

    @Override
    public List<MemoryEntry> search(String keyword, int limit) {
        QueryWrapper<MemoryEntryEntity> wrapper = new QueryWrapper<>();
        wrapper.like("content", keyword)
               .orderByDesc("created_at")
               .last("LIMIT " + Math.min(limit, 1000));
        return mapper.selectList(wrapper).stream()
                .map(this::fromEntity)
                .toList();
    }

    @Override
    public boolean deleteById(String id) {
        return mapper.deleteById(id) > 0;
    }

    // ---- 实体转换 ----

    private MemoryEntryEntity toEntity(MemoryEntry entry) {
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(entry.metadata());
        } catch (JsonProcessingException e) {
            metadataJson = "{}";
        }
        LocalDateTime createdAt = entry.createdAt() != null
                ? LocalDateTime.ofInstant(entry.createdAt(), ZoneId.systemDefault())
                : LocalDateTime.now();
        return new MemoryEntryEntity(entry.id(), entry.type(), entry.content(),
                metadataJson, createdAt);
    }

    private MemoryEntry fromEntity(MemoryEntryEntity entity) {
        @SuppressWarnings("unchecked")
        Map<String, String> metadata;
        try {
            metadata = objectMapper.readValue(
                    entity.getMetadata() != null ? entity.getMetadata() : "{}", Map.class);
        } catch (JsonProcessingException e) {
            metadata = Map.of();
        }
        Instant createdAt = entity.getCreatedAt() != null
                ? entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : Instant.now();
        return new MemoryEntry(entity.getId(), entity.getType(), entity.getContent(),
                metadata, createdAt);
    }
}
