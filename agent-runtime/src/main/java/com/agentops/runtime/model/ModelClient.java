package com.agentops.runtime.model;

/**
 * 模型调用客户端 — 平台与 LLM 厂商之间的集成边界。
 *
 * <p>这是 provider-agnostic 的抽象接口，当前实现使用 OpenAI Java SDK。
 * 未来如需切换到其他厂商（如 Anthropic、本地模型），只需提供新的实现，
 * 无需修改调用方代码。
 *
 * <p>典型调用流程（在 Agent 推理循环中）：
 * <ol>
 *   <li>构建 {@link ChatRequest}（包含系统 Prompt、对话历史、可用工具）</li>
 *   <li>调用 {@link #chat(ChatRequest)} 获取响应</li>
 *   <li>如果响应包含 toolCalls：通过 ToolRegistry 执行工具，
 *       将结果包装为 tool 类型 ChatMessage 追加到历史中，回到步骤 1</li>
 *   <li>如果响应为文本：递交给用户或下一步处理</li>
 * </ol>
 *
 * @see ChatRequest
 * @see ChatResponse
 */
public interface ModelClient {

    /**
     * 发送一次同步的对话请求。
     *
     * @param request 对话请求
     * @return 模型响应
     * @throws ModelClientException 如果调用失败（网络错误、API 错误等）
     */
    ChatResponse chat(ChatRequest request);

    // 后续版本扩展：
    // Flux<ChatResponse> chatStream(ChatRequest request);  — 流式输出
}
