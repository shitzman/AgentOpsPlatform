package com.agentops.runtime.model;

/**
 * 工具调用请求 — 模型要求执行某个工具的指令。
 *
 * <p>这是 provider-agnostic 的模型，与 {@link com.agentops.tools.ToolRegistry}
 * 配合使用：运行时收到 ToolCall 后，从 ToolRegistry 查找对应的 ToolExecutor 并执行。
 *
 * @param id        工具调用唯一 ID（模型生成，用于关联 ToolCall 和执行结果）
 * @param name      工具名称（在 ToolRegistry 中注册的名称）
 * @param arguments 工具参数 JSON 字符串（模型生成的参数）
 */
public record ToolCall(
        String id,
        String name,
        String arguments) {

    public ToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ToolCall id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ToolCall name 不能为空");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("ToolCall arguments 不能为 null");
        }
    }
}
