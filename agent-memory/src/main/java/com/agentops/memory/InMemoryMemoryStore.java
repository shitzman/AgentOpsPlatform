package com.agentops.memory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * MemoryStore 的内存实现 — 基于 {@link ConcurrentHashMap}，适合开发环境。
 *
 * <p>数据仅存于内存中，进程重启后丢失。生产环境应替换为基于 MySQL/PostgreSQL 的实现。
 */
public class InMemoryMemoryStore implements MemoryStore {

    private final ConcurrentMap<String, MemoryEntry> store = new ConcurrentHashMap<>();

    @Override
    public MemoryEntry save(MemoryEntry entry) {
        String id = entry.id();
        // 如果 id 是 PENDING，则自动生成
        if ("PENDING".equals(id)) {
            id = UUID.randomUUID().toString();
            entry = new MemoryEntry(id, entry.type(), entry.content(),
                    entry.metadata(), entry.createdAt());
        }
        store.put(id, entry);
        return entry;
    }

    @Override
    public Optional<MemoryEntry> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<MemoryEntry> findByType(String type, int limit) {
        return store.values().stream()
                .filter(e -> e.type().equals(type))
                .sorted(Comparator.comparing(MemoryEntry::createdAt).reversed())
                .limit(limit > 0 ? limit : Integer.MAX_VALUE)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryEntry> search(String keyword, int limit) {
        String lowerKeyword = keyword.toLowerCase();
        return store.values().stream()
                .filter(e -> e.content().toLowerCase().contains(lowerKeyword))
                .sorted(Comparator.comparing(MemoryEntry::createdAt).reversed())
                .limit(limit > 0 ? limit : Integer.MAX_VALUE)
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteById(String id) {
        return store.remove(id) != null;
    }
}
