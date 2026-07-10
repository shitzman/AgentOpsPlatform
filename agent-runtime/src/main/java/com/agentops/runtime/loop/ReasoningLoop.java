package com.agentops.runtime.loop;

import com.agentops.runtime.model.ChatMessage;
import com.agentops.runtime.model.ChatResponse;
import com.agentops.runtime.model.ToolCall;
import com.agentops.tools.core.ToolRegistry;

import java.util.List;

/**
 * 推理循环 — 封装 LLM 调用与工具执行的通用编排能力。
 *
 * <p>这是 {@code agent-runtime} 提供给所有领域 Agent 复用的核心能力：
 * <ul>
 *   <li>{@link #callLlm} — 单次 LLM 调用（含 span + 耗时日志）</li>
 *   <li>{@link #runWithAutoToolLoop} — 自动工具循环（LLM 请求工具 → 自动执行 → 回传 → 重复）</li>
 *   <li>{@link #executeToolCall} — 执行单个工具调用</li>
 * </ul>
 *
 * <p>领域 Agent（如 {@code DiagnosisOrchestrator}）通过此接口调用 LLM 和工具，
 * 无需直接依赖 {@code ModelClient}，也无需自行实现工具循环逻辑。
 *
 * @see com.agentops.runtime.model.ModelClient
 * @see ToolRegistry
 */
public interface ReasoningLoop {

    /**
     * 单次 LLM 调用，创建 {@code llm.chat} 子 Span 并记录耗时。
     *
     * @param messages        对话消息列表
     * @param toolRegistry    可用工具注册表（工具定义会传给 LLM）
     * @param temperature      采样温度
     * @param maxTokens       最大 token 数
     * @param responseFormat  响应格式（可为 null，表示不限制）
     * @return LLM 响应
     */
    ChatResponse callLlm(List<ChatMessage> messages, ToolRegistry toolRegistry,
                         double temperature, int maxTokens, String responseFormat);

    /**
     * 自动工具循环 — LLM 请求工具 → 自动执行 → 结果回传 → 继续推理，
     * 直到 LLM 返回纯文本或达到轮次上限。
     *
     * <p>与追问场景的「用户批准工具调用」不同，此方法自动执行所有工具调用，
     * 适用于初始分析流程（如诊断阶段），用户已在项目配置中启用了相应工具。
     *
     * @param messages        对话消息列表（会被原地修改，追加 tool_calls 和 tool 结果消息）
     * @param toolRegistry    可用工具注册表
     * @param temperature      采样温度
     * @param maxTokens       最大 token 数
     * @param responseFormat  响应格式（可为 null）
     * @param maxRounds       最大工具调用轮次
     * @return LLM 最终的文本回复
     */
    String runWithAutoToolLoop(List<ChatMessage> messages, ToolRegistry toolRegistry,
                               double temperature, int maxTokens, String responseFormat,
                               int maxRounds);

    /**
     * 执行单个工具调用（{@link ToolCall} 重载，用于自动工具循环中执行 LLM 请求的工具）。
     *
     * @param registry 工具注册表
     * @param call     工具调用请求
     * @return 结果文本（成功取 output，失败以 {@code ERROR:} 开头）
     */
    String executeToolCall(ToolRegistry registry, ToolCall call);

    /**
     * 执行单个工具调用（基本类型重载，用于用户批准的工具调用）。
     *
     * @param registry  工具注册表
     * @param id        工具调用 ID
     * @param name      工具名称
     * @param arguments 工具参数 JSON 字符串
     * @return 结果文本（成功取 output，失败以 {@code ERROR:} 开头）
     */
    String executeToolCall(ToolRegistry registry, String id, String name, String arguments);

    /**
     * 统计消息列表中的工具调用轮次（assistant tool_calls 消息数）。
     *
     * @param messages 消息列表
     * @return 工具调用轮次数
     */
    int countToolRounds(List<ChatMessage> messages);
}
