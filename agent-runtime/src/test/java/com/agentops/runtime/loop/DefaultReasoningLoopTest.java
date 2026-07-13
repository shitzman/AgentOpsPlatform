package com.agentops.runtime.loop;

import com.agentops.runtime.model.ChatMessage;
import com.agentops.runtime.model.ChatRequest;
import com.agentops.runtime.model.ChatResponse;
import com.agentops.runtime.model.ModelClient;
import com.agentops.runtime.model.ToolCall;
import com.agentops.tools.core.InMemoryToolRegistry;
import com.agentops.tools.core.ToolDefinition;
import com.agentops.tools.core.ToolExecutor;
import com.agentops.tools.core.ToolRegistry;
import com.agentops.tools.core.ToolResult;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * DefaultReasoningLoop 单元测试 — 验证 LLM 调用、自动工具循环、工具执行、轮次统计。
 *
 * <p>用 Mockito 桩 {@link ModelClient} 和 {@link Tracer}/{@link Span}，
 * 用真实 {@link InMemoryToolRegistry} + 桩 {@link ToolExecutor}。
 */
class DefaultReasoningLoopTest {

    private static final String MODEL_NAME = "deepseek-chat";

    private ModelClient modelClient;
    private Tracer tracer;
    private Span span;
    private Tracer.SpanInScope scope;
    private DefaultReasoningLoop reasoningLoop;

    @BeforeEach
    void setUp() {
        modelClient = mock(ModelClient.class);
        tracer = mock(Tracer.class);
        span = mock(Span.class);
        scope = mock(Tracer.SpanInScope.class);

        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(any())).thenReturn(scope);

        reasoningLoop = new DefaultReasoningLoop(modelClient, tracer, MODEL_NAME);
    }

    private ChatResponse textResponse(String content) {
        return new ChatResponse(content, List.of(), "stop");
    }

    private ChatResponse toolResponse(String toolName) {
        ToolCall call = new ToolCall("call-1", toolName, "{}");
        return new ChatResponse(null, List.of(call), "tool_calls");
    }

    private List<ChatMessage> singleUserMessage() {
        return new java.util.ArrayList<>(List.of(ChatMessage.user("诊断此堆栈")));
    }

    @Test
    @DisplayName("callLlm 返回响应并对 span 打标")
    void callLlm_returnsResponse_andTagsSpan() {
        ChatResponse expected = textResponse("hello");
        when(modelClient.chat(any(ChatRequest.class))).thenReturn(expected);

        ChatResponse actual = reasoningLoop.callLlm(
                singleUserMessage(), new InMemoryToolRegistry(), 0.2, 1024, null);

        assertEquals(expected, actual);
        verify(span).tag("model", MODEL_NAME);
        verify(span).tag(eq("finishReason"), anyString());
        verify(span).end();
    }

    @Test
    @DisplayName("runWithAutoToolLoop 无工具调用时直接返回文本")
    void runWithAutoToolLoop_noToolCalls_returnsContent() {
        when(modelClient.chat(any(ChatRequest.class))).thenReturn(textResponse("诊断完成"));

        String content = reasoningLoop.runWithAutoToolLoop(
                singleUserMessage(), new InMemoryToolRegistry(), 0.2, 1024, null, 8);

        assertEquals("诊断完成", content);
        verify(modelClient, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    @DisplayName("runWithAutoToolLoop 一轮工具调用后返回最终文本")
    void runWithAutoToolLoop_oneRoundThenText() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        ToolExecutor echo = args -> ToolResult.success("echoed");
        registry.register(new ToolDefinition("echo", "回显", Map.of()), echo);

        when(modelClient.chat(any(ChatRequest.class)))
                .thenReturn(toolResponse("echo"))
                .thenReturn(textResponse("done"));

        String content = reasoningLoop.runWithAutoToolLoop(
                singleUserMessage(), registry, 0.2, 1024, null, 8);

        assertEquals("done", content);
        verify(modelClient, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    @DisplayName("runWithAutoToolLoop 达到 maxRounds 后强制最终无工具调用")
    void runWithAutoToolLoop_hitsMaxRounds_forcesFinalResponseWithoutTools() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        ToolExecutor echo = args -> ToolResult.success("echoed");
        registry.register(new ToolDefinition("echo", "回显", Map.of()), echo);

        // 带工具的调用返回 tool_calls，不带工具的最终调用返回文本
        when(modelClient.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            ChatRequest req = invocation.getArgument(0);
            if (req.tools() == null || req.tools().isEmpty()) {
                return textResponse("forced final response");
            }
            return toolResponse("echo");
        });

        String content = reasoningLoop.runWithAutoToolLoop(
                singleUserMessage(), registry, 0.2, 1024, null, 2);

        // 达到 maxRounds 后，强制做一次不带工具的最终调用，返回文本
        assertEquals("forced final response", content);
        // 初始 1 次 + 每轮 1 次 × 2 轮 + 最终无工具 1 次 = 4 次
        verify(modelClient, times(4)).chat(any(ChatRequest.class));
    }

    @Test
    @DisplayName("callLlm 传 null ToolRegistry 时不抛 NPE，请求中工具列表为空")
    void callLlm_nullToolRegistry_sendsEmptyTools() {
        when(modelClient.chat(any(ChatRequest.class))).thenReturn(textResponse("ok"));

        ChatResponse actual = reasoningLoop.callLlm(
                singleUserMessage(), null, 0.2, 1024, null);

        assertEquals("ok", actual.content());
        verify(modelClient).chat(argThat(req -> req.tools() != null && req.tools().isEmpty()));
    }

    @Test
    @DisplayName("executeToolCall(ToolCall) 已注册工具返回输出")
    void executeToolCall_knownTool_returnsOutput() {
        ToolRegistry registry = new InMemoryToolRegistry();
        ToolExecutor echo = args -> ToolResult.success("echoed");
        ((InMemoryToolRegistry) registry).register(
                new ToolDefinition("echo", "回显", Map.of()), echo);

        String result = reasoningLoop.executeToolCall(registry,
                new ToolCall("c1", "echo", "{}"));

        assertEquals("echoed", result);
    }

    @Test
    @DisplayName("executeToolCall 未注册工具返回 ERROR")
    void executeToolCall_unknownTool_returnsError() {
        ToolRegistry registry = new InMemoryToolRegistry();

        String result = reasoningLoop.executeToolCall(registry, "c1", "nope", "{}");

        assertTrue(result.startsWith("ERROR: 未知工具"), "result=" + result);
    }

    @Test
    @DisplayName("executeToolCall(id,name,args) 重载版本同样执行")
    void executeToolCall_byIdNameArgs_overload() {
        ToolRegistry registry = new InMemoryToolRegistry();
        ToolExecutor echo = args -> ToolResult.success("echoed");
        ((InMemoryToolRegistry) registry).register(
                new ToolDefinition("echo", "回显", Map.of()), echo);

        String result = reasoningLoop.executeToolCall(registry, "c1", "echo", "{}");

        assertEquals("echoed", result);
    }

    @Test
    @DisplayName("countToolRounds 统计 assistant 工具调用消息数")
    void countToolRounds_countsAssistantToolCallsMessages() {
        ToolCall call = new ToolCall("c1", "echo", "{}");
        List<ChatMessage> messages = List.of(
                ChatMessage.system("sys"),
                ChatMessage.user("hi"),
                ChatMessage.assistantToolCalls(List.of(call)),
                ChatMessage.tool("c1", "result"),
                ChatMessage.assistant("text"),
                ChatMessage.assistantToolCalls(List.of(call)),
                ChatMessage.tool("c1", "result2"));

        int rounds = reasoningLoop.countToolRounds(messages);

        assertEquals(2, rounds);
    }
}
