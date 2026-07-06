/**
 * Agent Runtime — 模型编排、推理循环、Tool Calling、流式输出和权限控制。
 *
 * <h3>model 子包</h3>
 * <ul>
 *   <li>{@link com.agentops.runtime.model.ChatMessage} — 对话消息（system/user/assistant/tool）</li>
 *   <li>{@link com.agentops.runtime.model.ToolCall} — 工具调用请求</li>
 *   <li>{@link com.agentops.runtime.model.ChatRequest} — 对话请求</li>
 *   <li>{@link com.agentops.runtime.model.ChatResponse} — 对话响应</li>
 *   <li>{@link com.agentops.runtime.model.ModelClient} — 模型调用边界接口</li>
 * </ul>
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@link com.agentops.runtime.OpenAIModelClient} — 基于 HttpClient 的 OpenAI 兼容客户端</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * Runtime 层不包含领域诊断逻辑，只负责模型编排和工具调用协调。
 */
package com.agentops.runtime;
