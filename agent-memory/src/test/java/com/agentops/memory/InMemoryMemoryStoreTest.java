package com.agentops.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryMemoryStore 单元测试。
 */
class InMemoryMemoryStoreTest {

    private InMemoryMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryMemoryStore();
    }

    @Test
    @DisplayName("保存并查找 Memory 条目")
    void saveAndFind() {
        MemoryEntry entry = MemoryEntry.now("id-1", "diagnosis", "NullPointerException 诊断结果");
        store.save(entry);

        var found = store.findById("id-1");
        assertTrue(found.isPresent());
        assertEquals("diagnosis", found.get().type());
        assertEquals("NullPointerException 诊断结果", found.get().content());
    }

    @Test
    @DisplayName("PENDING ID 自动生成 UUID")
    void pendingAutoGeneratesId() {
        MemoryEntry entry = MemoryEntry.pending("conversation", "用户询问异常");
        MemoryEntry saved = store.save(entry);

        assertNotEquals("PENDING", saved.id());
        assertFalse(saved.id().isBlank());
    }

    @Test
    @DisplayName("按类型查找并限制数量")
    void findByTypeWithLimit() {
        store.save(MemoryEntry.now("1", "diagnosis", "d1"));
        store.save(MemoryEntry.now("2", "diagnosis", "d2"));
        store.save(MemoryEntry.now("3", "conversation", "c1"));

        List<MemoryEntry> results = store.findByType("diagnosis", 1);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("按关键词搜索")
    void searchByKeyword() {
        store.save(MemoryEntry.now("1", "diagnosis", "空指针异常在 OrderService"));
        store.save(MemoryEntry.now("2", "diagnosis", "数组越界在 ListUtil"));
        store.save(MemoryEntry.now("3", "conversation", "用户反馈订单异常"));

        List<MemoryEntry> results = store.search("OrderService", 10);
        assertEquals(1, results.size());
        assertEquals("1", results.get(0).id());
    }

    @Test
    @DisplayName("删除不存在的条目返回 false")
    void deleteNonExistent() {
        assertFalse(store.deleteById("nonexistent"));
    }

    @Test
    @DisplayName("删除后无法查找到")
    void deleteRemoves() {
        store.save(MemoryEntry.now("id", "test", "content"));
        assertTrue(store.deleteById("id"));
        assertTrue(store.findById("id").isEmpty());
    }
}
