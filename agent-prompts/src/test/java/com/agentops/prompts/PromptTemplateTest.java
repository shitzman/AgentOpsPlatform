package com.agentops.prompts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptTemplate 单元测试 — 覆盖模板渲染、变量提取和异常场景。
 */
class PromptTemplateTest {

    @Test
    @DisplayName("渲染不含占位符的模板")
    void renderPlainText() {
        PromptTemplate t = new PromptTemplate("test", "你好，世界");
        assertEquals("你好，世界", t.render(Map.of()));
    }

    @Test
    @DisplayName("渲染含单个占位符的模板")
    void renderSinglePlaceholder() {
        PromptTemplate t = new PromptTemplate("test", "你好，{{name}}");
        assertEquals("你好，张三", t.render(Map.of("name", "张三")));
    }

    @Test
    @DisplayName("渲染含多个占位符的模板")
    void renderMultiplePlaceholders() {
        PromptTemplate t = new PromptTemplate("test",
                "异常类型：{{type}}，消息：{{msg}}");
        String result = t.render(Map.of("type", "NPE", "msg", "null pointer"));
        assertEquals("异常类型：NPE，消息：null pointer", result);
    }

    @Test
    @DisplayName("缺少变量时抛出异常")
    void renderMissingVariableThrows() {
        PromptTemplate t = new PromptTemplate("test", "{{name}} 出错了");
        assertThrows(IllegalArgumentException.class,
                () -> t.render(Map.of()));
    }

    @Test
    @DisplayName("提取模板中的变量名")
    void extractVariables() {
        PromptTemplate t = new PromptTemplate("test",
                "{{a}} + {{b}} = {{a}}");
        Map<String, Object> vars = t.extractVariables();
        assertEquals(2, vars.size());
        assertTrue(vars.containsKey("a"));
        assertTrue(vars.containsKey("b"));
    }

    @Test
    @DisplayName("空名称抛出异常")
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new PromptTemplate("  ", "content"));
    }

    @Test
    @DisplayName("空模板文本允许")
    void emptyTemplateAllowed() {
        PromptTemplate t = new PromptTemplate("test", "");
        assertEquals("", t.render(Map.of()));
    }
}
