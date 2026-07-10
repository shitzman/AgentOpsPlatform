package com.agentops.api.controller;

import com.agentops.api.dto.ChatFollowUpRequest;
import com.agentops.api.dto.DiagnosisRequest;
import com.agentops.api.dto.ToolApproveRequest;
import com.agentops.api.service.DiagnosisReportPersistenceService;
import com.agentops.api.service.DiagnosisService;
import com.agentops.api.vo.ChatReplyVo;
import com.agentops.api.vo.DiagnosisHistoryResponseVo;
import com.agentops.api.vo.DiagnosisResponseVo;
import com.agentops.api.vo.HealthVo;
import com.agentops.api.vo.PendingToolCallVo;
import com.agentops.api.vo.ToolExecutionResultVo;
import com.agentops.prompts.PromptRegistry;
import com.agentops.runtime.model.ToolCall;
import com.agentops.tools.core.ToolRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 诊断 REST API — 支持多轮对话、工具调用和多源上下文关联诊断。
 *
 * <p>本类仅做 HTTP 适配：DTO 绑定、Span 管理、VO 构造。业务逻辑由
 * {@link DiagnosisService} 和 {@link DiagnosisReportPersistenceService} 承担。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/diagnosis — 提交诊断（多源上下文注入）</li>
 *   <li>GET  /api/diagnosis — 诊断历史查询</li>
 *   <li>POST /api/chat       — 多轮对话（追问、工具调用）</li>
 *   <li>GET  /api/health     — 健康检查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class DiagnosisController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisController.class);

    private final DiagnosisService diagnosisService;
    private final DiagnosisReportPersistenceService reportPersistenceService;
    private final PromptRegistry promptRegistry;
    private final ToolRegistry toolRegistry;
    private final Tracer tracer;

    public DiagnosisController(DiagnosisService diagnosisService,
                               DiagnosisReportPersistenceService reportPersistenceService,
                               PromptRegistry promptRegistry,
                               ToolRegistry toolRegistry,
                               Tracer tracer) {
        this.diagnosisService = diagnosisService;
        this.reportPersistenceService = reportPersistenceService;
        this.promptRegistry = promptRegistry;
        this.toolRegistry = toolRegistry;
        this.tracer = tracer;
    }

    /**
     * 提交异常堆栈或日志内容进行诊断（支持堆栈模式 + 纯日志分析模式）。
     *
     * <pre>
     * POST /api/diagnosis
     * {
     *   "stackTrace": "java.lang.NullPointerException: ...",  // 可选（有 logContent 时）
     *   "logContent": "原始日志文本...",   // 可选（有 stackTrace 时），纯日志模式时为主要输入
     *   "conversationId": "uuid-xxx",     // 可选，用于多轮对话
     *   "projectId": "uuid-xxx"           // 可选，启用多源上下文（环境+Git+日志）
     * }
     * </pre>
     */
    @PostMapping("/diagnosis")
    public DiagnosisResponseVo diagnose(@RequestBody DiagnosisRequest request) {
        Span span = tracer.nextSpan().name("POST /api/diagnosis").start();
        String mode = (request.stackTrace() == null || request.stackTrace().isBlank())
                ? "log-analysis" : "stack-trace";
        log.info("[diagnose] start: mode={}, conversationId={}, projectId={}",
                mode, request.conversationId(), request.projectId());
        try (var scope = tracer.withSpan(span)) {
            DiagnosisService.DiagnosisResult result = diagnosisService.diagnose(
                    request.stackTrace(), request.logContent(),
                    request.conversationId(), request.projectId());
            log.info("[diagnose] done: conversationId={}, reportSeverity={}",
                    result.conversationId(), result.report().severity());
            return DiagnosisResponseVo.ok(result.report(), result.conversationId());
        } catch (Exception e) {
            span.tag("error", e.getMessage());
            log.warn("[diagnose] failed: {}", e.getMessage(), e);
            return DiagnosisResponseVo.error(e.getMessage());
        } finally {
            span.end();
        }
    }

    /**
     * 诊断历史查询 — 按项目和分页查询历史诊断报告。
     *
     * <pre>
     * GET /api/diagnosis?projectId=X&page=0&size=20
     * </pre>
     */
    @GetMapping("/diagnosis")
    public DiagnosisHistoryResponseVo listDiagnosis(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            DiagnosisReportPersistenceService.ListResult result =
                    reportPersistenceService.listReports(projectId, page, size);
            return DiagnosisHistoryResponseVo.ok(result.reports(), result.total(), result.page(), result.size());
        } catch (Exception e) {
            return DiagnosisHistoryResponseVo.error(e.getMessage());
        }
    }

    /**
     * 多轮对话端点 — 在已有诊断基础上追问。
     *
     * <p>LLM 可能返回待批准的工具调用（{@code pendingToolCalls}），此时前端展示工具批准 UI，
     * 用户批准后调 {@code POST /api/chat/continue} 继续推理循环。
     */
    @PostMapping("/chat")
    public ChatReplyVo chat(@RequestBody ChatFollowUpRequest request) {
        Span span = tracer.nextSpan().name("POST /api/chat").start();
        int msgLen = request.message() == null ? 0 : request.message().length();
        log.info("[chat] start: conversationId={}, messageLen={}", request.conversationId(), msgLen);
        try (var scope = tracer.withSpan(span)) {
            DiagnosisService.ChatResult result = diagnosisService.chat(
                    request.conversationId(), request.message(), request.projectId());
            log.info("[chat] done: conversationId={}, hasPendingToolCalls={}, toolRound={}",
                    result.conversationId(), result.hasPendingToolCalls(), result.toolRound());
            return toChatReplyVo(result);
        } catch (Exception e) {
            span.tag("error", e.getMessage());
            log.warn("[chat] failed: {}", e.getMessage(), e);
            return ChatReplyVo.error(e.getMessage());
        } finally {
            span.end();
        }
    }

    /**
     * 工具调用批准端点 — 执行用户批准的工具调用并继续推理循环。
     *
     * <p>可能返回新的待批准工具调用（继续循环），或最终文本回复（循环结束）。
     */
    @PostMapping("/chat/continue")
    public ChatReplyVo continueChat(@RequestBody ToolApproveRequest request) {
        Span span = tracer.nextSpan().name("POST /api/chat/continue").start();
        int approvedCount = request.approvedToolCalls() == null ? 0 : request.approvedToolCalls().size();
        log.info("[continueChat] start: sessionId={}, approvedToolCalls={}", request.sessionId(), approvedCount);
        try (var scope = tracer.withSpan(span)) {
            DiagnosisService.ChatResult result = diagnosisService.continueWithTools(
                    request.sessionId(), request.approvedToolCalls());
            log.info("[continueChat] done: sessionId={}, hasPendingToolCalls={}, toolRound={}",
                    request.sessionId(), result.hasPendingToolCalls(), result.toolRound());
            return toChatReplyVo(result);
        } catch (Exception e) {
            span.tag("error", e.getMessage());
            log.warn("[continueChat] failed: {}", e.getMessage(), e);
            return ChatReplyVo.error(e.getMessage());
        } finally {
            span.end();
        }
    }

    /** 将 ChatResult 映射为 ChatReplyVo（待批准工具调用 vs 纯文本回复），携带执行结果和轮次信息 */
    private static ChatReplyVo toChatReplyVo(DiagnosisService.ChatResult result) {
        List<ToolExecutionResultVo> executed = result.executedToolResults() == null ? null
                : result.executedToolResults().stream()
                .map(e -> new ToolExecutionResultVo(e.id(), e.name(), e.arguments(), e.result(), e.success()))
                .toList();

        if (result.hasPendingToolCalls()) {
            List<PendingToolCallVo> pending = result.pendingToolCalls().stream()
                    .map(tc -> new PendingToolCallVo(tc.id(), tc.name(), tc.arguments()))
                    .toList();
            return ChatReplyVo.pending(result.sessionId(), pending, result.conversationId(), result.traceId(),
                    executed, result.toolRound(), result.maxToolRounds());
        }
        return ChatReplyVo.ok(result.reply(), result.conversationId(), result.traceId(),
                executed, result.toolRound(), result.maxToolRounds());
    }

    @GetMapping("/health")
    public HealthVo health() {
        return new HealthVo(
                "UP",
                "1.0.0-SNAPSHOT",
                promptRegistry.listNames().size() + " loaded",
                toolRegistry.listDefinitions().size() + " registered");
    }
}
