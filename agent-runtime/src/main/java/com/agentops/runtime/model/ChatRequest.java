package com.agentops.runtime.model;

import com.agentops.tools.ToolDefinition;

import java.util.Collections;
import java.util.List;

/**
 * 对话请求 — 封装一次 LLM 调用的全部参数。
 *
 * <p>包含对话历史、可用工具列表、模型选择和生成参数。
 * 这是 provider-agnostic 的请求模型，{@link ModelClient} 的实现
 * 负责将其转换为具体 LLM 厂商的 API 格式。
 *
 * @param messages    对话历史（按时间顺序）
 * @param tools       当前可用的工具定义列表（来自 ToolRegistry）
 * @param model       模型名称（如 "gpt-4o"）
 * @param temperature 生成温度 (0.0–2.0)，诊断场景建议 0.0–0.3
 * @param maxTokens   最大输出 Token 数
 */
public record ChatRequest(
        List<ChatMessage> messages,
        List<ToolDefinition> tools,
        String model,
        double temperature,
        int maxTokens) {

    public ChatRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("对话消息列表不能为空");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature 必须在 0.0–2.0 之间");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens 必须 >= 1");
        }
        messages = Collections.unmodifiableList(messages);
        tools = tools != null
                ? Collections.unmodifiableList(tools)
                : Collections.emptyList();
    }

    /** 创建一个不带工具的简单对话请求 */
    public static ChatRequest simple(List<ChatMessage> messages, String model) {
        return new ChatRequest(messages, Collections.emptyList(), model, 0.0, 4096);
    }
}
