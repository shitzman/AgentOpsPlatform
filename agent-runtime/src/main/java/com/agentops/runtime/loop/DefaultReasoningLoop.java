package com.agentops.runtime.loop;

import com.agentops.runtime.model.ChatMessage;
import com.agentops.runtime.model.ChatRequest;
import com.agentops.runtime.model.ChatResponse;
import com.agentops.runtime.model.ModelClient;
import com.agentops.runtime.model.ToolCall;
import com.agentops.tools.core.ToolExecutor;
import com.agentops.tools.core.ToolRegistry;
import com.agentops.tools.core.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ReasoningLoop} 的默认实现 — 封装 LLM 调用 + 自动工具循环 + 工具执行。
 *
 * <p>从 {@code DiagnosisService} 抽取的通用编排能力，任何领域 Agent 均可复用。
 * 构造注入 {@link ModelClient}、{@link Tracer} 和模型名称。
 */
public class DefaultReasoningLoop implements ReasoningLoop {

    private static final Logger log = LoggerFactory.getLogger(DefaultReasoningLoop.class);

    private final ModelClient modelClient;
    private final Tracer tracer;
    private final String modelName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DefaultReasoningLoop(ModelClient modelClient, Tracer tracer, String modelName) {
        this.modelClient = modelClient;
        this.tracer = tracer;
        this.modelName = modelName;
    }

    @Override
    public ChatResponse callLlm(List<ChatMessage> messages, ToolRegistry toolRegistry,
                               double temperature, int maxTokens, String responseFormat) {
        Span llmSpan = tracer.nextSpan().name("llm.chat").start();
        long start = System.nanoTime();
        try (var scope = tracer.withSpan(llmSpan)) {
            llmSpan.tag("model", modelName);
            ChatRequest request = new ChatRequest(
                    messages,
                    toolRegistry.listDefinitions(),
                    modelName,
                    temperature,
                    maxTokens,
                    responseFormat);
            ChatResponse response = modelClient.chat(request);
            String finishReason = response.finishReason() != null ? response.finishReason() : "unknown";
            llmSpan.tag("finishReason", finishReason);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("[callLlm] model={}, temp={}, maxTokens={}, finishReason={}, elapsedMs={}",
                    modelName, temperature, maxTokens, finishReason, elapsedMs);
            return response;
        } finally {
            llmSpan.end();
        }
    }

    @Override
    public String runWithAutoToolLoop(List<ChatMessage> messages, ToolRegistry toolRegistry,
                                      double temperature, int maxTokens, String responseFormat,
                                      int maxRounds) {
        ChatResponse response = callLlm(messages, toolRegistry, temperature, maxTokens, responseFormat);

        int round = 0;
        while (response.hasToolCalls() && round < maxRounds) {
            messages.add(ChatMessage.assistantToolCalls(response.toolCalls()));
            log.info("[runWithAutoToolLoop] round={}, toolCalls={}", round + 1, response.toolCalls().size());

            for (ToolCall call : response.toolCalls()) {
                log.debug("[runWithAutoToolLoop] executing tool: name={}, argsLen={}",
                        call.name(), call.arguments() == null ? 0 : call.arguments().length());
                String resultText = executeToolCall(toolRegistry, call);
                messages.add(ChatMessage.tool(call.id(), resultText));
            }

            round++;
            tagSpan("toolLoop.round", String.valueOf(round));

            response = callLlm(messages, toolRegistry, temperature, maxTokens, responseFormat);
        }

        log.info("[runWithAutoToolLoop] finished: rounds={}, hasToolCalls={}, contentLen={}",
                round, response.hasToolCalls(),
                response.content() == null ? 0 : response.content().length());
        return response.content();
    }

    @Override
    public String executeToolCall(ToolRegistry registry, ToolCall call) {
        return doExecuteToolCall(registry, call.id(), call.name(), call.arguments());
    }

    @Override
    public String executeToolCall(ToolRegistry registry, String id, String name, String arguments) {
        return doExecuteToolCall(registry, id, name, arguments);
    }

    @Override
    public int countToolRounds(List<ChatMessage> messages) {
        int count = 0;
        for (ChatMessage msg : messages) {
            if ("assistant".equals(msg.role()) && !msg.toolCalls().isEmpty()) count++;
        }
        return count;
    }

    // ---- 内部方法 ----

    private String doExecuteToolCall(ToolRegistry registry, String id, String name, String arguments) {
        Optional<ToolExecutor> executorOpt = registry.getExecutor(name);
        if (executorOpt.isEmpty()) {
            log.debug("[executeToolCall] unknown tool: name={}", name);
            return "ERROR: 未知工具 " + name;
        }
        try {
            Map<String, Object> args = parseToolArguments(arguments);
            ToolResult result = executorOpt.get().execute(args);
            log.debug("[executeToolCall] done: name={}, success={}", name, result.success());
            return result.success() ? result.output() : "ERROR: " + result.error();
        } catch (Exception e) {
            log.debug("[executeToolCall] failed: name={}, error={}", name, e.getMessage());
            return "ERROR: 工具执行失败 " + name + " — " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(arguments, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("工具参数 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private void tagSpan(String key, String value) {
        if (tracer != null) {
            Span current = tracer.currentSpan();
            if (current != null) current.tag(key, value);
        }
    }
}
