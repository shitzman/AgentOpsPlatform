package com.agentops.business.exceptionagent;

import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.prompts.PromptRegistry;
import com.agentops.runtime.loop.ReasoningLoop;
import com.agentops.runtime.model.ChatMessage;
import com.agentops.tools.core.InMemoryToolRegistry;
import com.agentops.tools.core.ToolRegistry;
import com.agentops.workflow.SequentialWorkflowEngine;
import com.agentops.workflow.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DiagnosisOrchestrator 单元测试 — 验证诊断分支、报告解析、fallback、traceId 传递。
 *
 * <p>用 Mockito 桩 {@link ReasoningLoop}（返回固定 JSON/文本）和 {@link PromptRegistry}，
 * 用真实 {@link SequentialWorkflowEngine} + {@link BusinessExceptionAgent}（构造时注册工作流）。
 */
class DiagnosisOrchestratorTest {

    private static final String VALID_STACK =
            "java.lang.NullPointerException: test message\n\tat com.example.OrderService.create(OrderService.java:42)";

    private static final String VALID_REPORT_JSON =
            "{\"summary\":\"NPE in OrderService\","
            + "\"exceptionType\":\"java.lang.NullPointerException\","
            + "\"severity\":\"high\","
            + "\"likelyRootCause\":\"未判空即调用方法\","
            + "\"impactScope\":\"订单服务\","
            + "\"urgency\":\"立即修复\","
            + "\"relatedModules\":[\"order-service\"],"
            + "\"recommendations\":[\"添加 null 检查\"],"
            + "\"confidence\":0.85,"
            + "\"gitBlameHints\":[],"
            + "\"environmentFactors\":[],"
            + "\"followUpQuestions\":[]}";

    private ReasoningLoop reasoningLoop;
    private PromptRegistry promptRegistry;
    private DiagnosisOrchestrator orchestrator;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        reasoningLoop = mock(ReasoningLoop.class);
        promptRegistry = mock(PromptRegistry.class);
        when(promptRegistry.render(anyString(), anyMap())).thenReturn("rendered system prompt");

        WorkflowEngine engine = new SequentialWorkflowEngine();
        // 构造时向 engine 注册 business-exception-diagnosis 工作流
        new BusinessExceptionAgent(engine);
        orchestrator = new DiagnosisOrchestrator(reasoningLoop, promptRegistry, engine);
        toolRegistry = new InMemoryToolRegistry();
    }

    @Test
    @DisplayName("堆栈模式：调用 runWithAutoToolLoop 并解析为 DiagnosisReport")
    void diagnose_stackTraceMode_callsRunWithAutoToolLoop_andParsesReport() {
        when(reasoningLoop.runWithAutoToolLoop(anyList(), any(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn(VALID_REPORT_JSON);

        DiagnosisOutcome outcome = orchestrator.diagnose(
                VALID_STACK, null, null, toolRegistry, List.of(), "trace-1");

        DiagnosisReport report = outcome.report();
        assertEquals("NPE in OrderService", report.summary());
        assertEquals("java.lang.NullPointerException", report.exceptionType());
        assertEquals("high", report.severity());
        assertEquals(0.85, report.confidence());
        assertEquals("trace-1", report.traceId());
        assertEquals(List.of("添加 null 检查"), report.recommendations());
        verify(reasoningLoop, times(1)).runWithAutoToolLoop(
                anyList(), any(), anyDouble(), anyInt(), any(), anyInt());
    }

    @Test
    @DisplayName("纯日志模式：不抛异常并返回报告")
    void diagnose_logOnlyMode_returnsReport() {
        String logJson = VALID_REPORT_JSON.replace("java.lang.NullPointerException", "LogParseException");
        when(reasoningLoop.runWithAutoToolLoop(anyList(), any(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn(logJson);

        DiagnosisOutcome outcome = orchestrator.diagnose(
                null, "ERROR something broke", null, toolRegistry, List.of(), "trace-2");

        assertEquals("LogParseException", outcome.report().exceptionType());
        assertEquals("trace-2", outcome.report().traceId());
    }

    @Test
    @DisplayName("JSON 解析失败时返回 fallback 报告")
    void diagnose_invalidJson_returnsFallbackReport() {
        when(reasoningLoop.runWithAutoToolLoop(anyList(), any(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn("this is not json at all");

        DiagnosisOutcome outcome = orchestrator.diagnose(
                VALID_STACK, null, null, toolRegistry, List.of(), "trace-3");

        DiagnosisReport report = outcome.report();
        assertEquals("JSON 解析失败，以下是原始诊断内容", report.summary());
        assertEquals("java.lang.NullPointerException", report.exceptionType());
        assertEquals("medium", report.severity());
        assertEquals(0.0, report.confidence());
        assertEquals("trace-3", report.traceId());
    }

    @Test
    @DisplayName("projectInfo 为 null 时不构建多源上下文")
    void diagnose_noProjectInfo_noEnrichment() {
        when(reasoningLoop.runWithAutoToolLoop(anyList(), any(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn(VALID_REPORT_JSON);

        assertDoesNotThrow(() -> orchestrator.diagnose(
                VALID_STACK, null, null, toolRegistry, List.of(), "trace-4"));
    }

    @Test
    @DisplayName("projectInfo 有值但 repoPath 为 null 时不报错")
    void diagnose_withProjectInfo_nullRepoPath_noGitContext() {
        when(reasoningLoop.runWithAutoToolLoop(anyList(), any(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn(VALID_REPORT_JSON);
        ProjectInfo info = new ProjectInfo("p1", "demo", "desc", null);

        assertDoesNotThrow(() -> orchestrator.diagnose(
                VALID_STACK, null, info, toolRegistry, List.of(), "trace-5"));
    }

    @Test
    @DisplayName("LLM 返回 markdown 代码块包裹的 JSON 也能正确解析")
    void diagnose_markdownFencedJson_parsedCorrectly() {
        String fenced = "```json\n" + VALID_REPORT_JSON + "\n```";
        when(reasoningLoop.runWithAutoToolLoop(anyList(), any(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn(fenced);

        DiagnosisOutcome outcome = orchestrator.diagnose(
                VALID_STACK, null, null, toolRegistry, List.of(), "trace-6");

        assertEquals("NPE in OrderService", outcome.report().summary());
    }

    @Test
    @DisplayName("traceId 透传到诊断报告")
    void diagnose_carriesTraceId_intoReport() {
        when(reasoningLoop.runWithAutoToolLoop(anyList(), any(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn(VALID_REPORT_JSON);

        DiagnosisOutcome outcome = orchestrator.diagnose(
                VALID_STACK, null, null, toolRegistry, List.of(), "my-trace-id-123");

        assertEquals("my-trace-id-123", outcome.report().traceId());
    }
}
