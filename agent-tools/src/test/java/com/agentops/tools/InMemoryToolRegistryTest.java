package com.agentops.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryToolRegistry 单元测试。
 */
class InMemoryToolRegistryTest {

    private InMemoryToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryToolRegistry();
    }

    @Test
    @DisplayName("注册并查找工具")
    void registerAndFind() {
        ToolDefinition def = new ToolDefinition("log", "查询日志",
                Map.of("type", "object"));
        ToolExecutor executor = args -> ToolResult.success("ok");

        registry.register(def, executor);

        assertTrue(registry.getDefinition("log").isPresent());
        assertTrue(registry.getExecutor("log").isPresent());
        assertEquals("查询日志", registry.getDefinition("log").get().description());
    }

    @Test
    @DisplayName("重复注册同名工具抛出异常")
    void duplicateRegistrationThrows() {
        ToolDefinition def = new ToolDefinition("log", "desc", Map.of());
        registry.register(def, args -> ToolResult.success("ok"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(def, args -> ToolResult.success("ok2")));
    }

    @Test
    @DisplayName("注销后查找返回空")
    void unregisterRemoves() {
        ToolDefinition def = new ToolDefinition("log", "desc", Map.of());
        registry.register(def, args -> ToolResult.success("ok"));
        registry.unregister("log");

        assertTrue(registry.getDefinition("log").isEmpty());
        assertTrue(registry.getExecutor("log").isEmpty());
    }

    @Test
    @DisplayName("列出所有工具定义")
    void listDefinitions() {
        registry.register(new ToolDefinition("a", "da", Map.of()),
                args -> ToolResult.success("a"));
        registry.register(new ToolDefinition("b", "db", Map.of()),
                args -> ToolResult.success("b"));

        assertEquals(2, registry.listDefinitions().size());
    }

    @Test
    @DisplayName("查找不存在的工具返回空")
    void notFoundReturnsEmpty() {
        assertTrue(registry.getDefinition("nonexistent").isEmpty());
        assertTrue(registry.getExecutor("nonexistent").isEmpty());
    }
}
