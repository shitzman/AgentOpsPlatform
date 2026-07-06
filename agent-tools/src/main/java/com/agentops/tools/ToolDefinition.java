package com.agentops.tools;

import java.util.Map;

/**
 * 工具定义 — 描述一个可注册到 {@link ToolRegistry} 的工具元数据。
 *
 * <p>{@code parameters} 是一个 JSON Schema 格式的 Map，定义工具的输入参数契约。
 * 此格式与 OpenAI function calling 以及其他 LLM 的 tool-use API 兼容，
 * 可以直接序列化为 JSON 传给模型。
 *
 * @param name        工具名称，全局唯一，注册后不可变
 * @param description 工具描述，展示给 LLM 帮助其理解工具的用途
 * @param parameters  JSON Schema 描述的工具参数定义
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters) {

    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("工具描述不能为空");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("参数定义不能为 null");
        }
    }
}
