package com.agentops.api.service;

import com.agentops.api.dto.ToolApproveRequest;
import com.agentops.business.exceptionagent.BusinessExceptionAgent;
import com.agentops.business.exceptionagent.DiagnosisOrchestrator;
import com.agentops.business.exceptionagent.DiagnosisOutcome;
import com.agentops.business.exceptionagent.ProjectInfo;
import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.prompts.PromptRegistry;
import com.agentops.repository.MySqlProjectManager;
import com.agentops.repository.entity.ProjectEntity;
import com.agentops.runtime.loop.ReasoningLoop;
import com.agentops.runtime.model.ChatMessage;
import com.agentops.runtime.model.ChatResponse;
import com.agentops.runtime.model.ToolCall;
import com.agentops.tools.core.ToolRegistry;
import com.agentops.tools.log.LogExtractor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 诊断 delivery 服务 — HTTP 层与领域编排之间的适配层。
 *
 * <p>职责（仅 delivery 关注点）：
 * <ul>
 *   <li>入口校验（堆栈/日志非空检查）</li>
 *   <li>项目解析（ProjectEntity → ProjectInfo + ToolRegistry）</li>
 *   <li>对话历史加载</li>
 *   <li>委托 {@link DiagnosisOrchestrator} 执行领域诊断</li>
 *   <li>持久化报告 + 保存对话历史</li>
 *   <li>多轮追问 + 工具批准会话管理</li>
 *   <li>Span 打标</li>
 * </ul>
 *
 * <p>领域编排（LLM 调用、工具循环、多源上下文、报告解析）由
 * {@link DiagnosisOrchestrator} 和 {@link ReasoningLoop} 承担。
 */
@Service
public class DiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);

    private static final int MAX_TOOL_ROUNDS = 8;

    private final DiagnosisOrchestrator diagnosisOrchestrator;
    private final ReasoningLoop reasoningLoop;
    private final PromptRegistry promptRegistry;
    private final ToolRegistry toolRegistry;
    private final MySqlProjectManager projectManager;
    private final ConversationService conversationService;
    private final DiagnosisReportPersistenceService reportPersistenceService;
    private final ToolCallSessionStore sessionStore;
    private final Tracer tracer;

    public DiagnosisService(DiagnosisOrchestrator diagnosisOrchestrator,
                            ReasoningLoop reasoningLoop,
                            PromptRegistry promptRegistry,
                            ToolRegistry toolRegistry,
                            MySqlProjectManager projectManager,
                            ConversationService conversationService,
                            DiagnosisReportPersistenceService reportPersistenceService,
                            ToolCallSessionStore sessionStore,
                            Tracer tracer) {
        this.diagnosisOrchestrator = diagnosisOrchestrator;
        this.reasoningLoop = reasoningLoop;
        this.promptRegistry = promptRegistry;
        this.toolRegistry = toolRegistry;
        this.projectManager = projectManager;
        this.conversationService = conversationService;
        this.reportPersistenceService = reportPersistenceService;
        this.sessionStore = sessionStore;
        this.tracer = tracer;
    }

    // ========================================================================
    // 公共入口
    // ========================================================================

    /**
     * 执行诊断（堆栈模式或纯日志模式）。
     *
     * @throws IllegalArgumentException 未提供 stackTrace 且未提供 logContent 时
     */
    public DiagnosisResult diagnose(String stackTrace, String logContent,
                                    String conversationId, String projectId) {
        boolean hasStackTrace = isNotBlank(stackTrace);
        boolean hasLogContent = isNotBlank(logContent);

        if (!hasStackTrace && !hasLogContent) {
            throw new IllegalArgumentException("请提供异常堆栈（stackTrace）或日志内容（logContent）");
        }

        // 无堆栈但有日志时，尝试自动提取堆栈
        String rawStackTrace = stackTrace;
        if (!hasStackTrace) {
            String extracted = LogExtractor.extractStackTrace(logContent);
            if (extracted != null) {
                rawStackTrace = extracted;
                hasStackTrace = true;
            }
        }

        String mode = hasStackTrace ? "stack-trace" : "log-analysis";
        log.info("[diagnose] start: mode={}, hasStackTrace={}, hasLogContent={}, conversationId={}, projectId={}",
                mode, hasStackTrace, hasLogContent,
                conversationId != null ? conversationId : "new", projectId);

        tag("mode", mode);
        tag("conversationId", conversationId != null ? conversationId : "new");
        if (projectId != null) tag("projectId", projectId);

        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);
        ProjectInfo projectInfo = resolveProjectInfo(projectId);
        List<ChatMessage> priorMessages = loadPriorMessages(conversationId);
        String traceId = currentTraceId();

        DiagnosisOutcome outcome = diagnosisOrchestrator.diagnose(
                hasStackTrace ? rawStackTrace : null,
                logContent,
                projectInfo,
                activeToolRegistry,
                priorMessages,
                traceId);

        DiagnosisReport report = outcome.report();
        reportPersistenceService.saveReport(projectId, report,
                hasStackTrace ? rawStackTrace : logContent);
        String cid = conversationService.save(conversationId, outcome.messages(),
                ChatMessage.assistant(outcome.rawContent()));

        tag("report.severity", report.severity());
        tag("report.confidence", String.valueOf(report.confidence()));
        log.info("[diagnose] done: conversationId={}, reportSeverity={}", cid, report.severity());

        return new DiagnosisResult(report, cid);
    }

    /**
     * 多轮追问。
     *
     * @throws IllegalArgumentException conversationId 或 message 为空时
     */
    public ChatResult chat(String conversationId, String userMessage, String projectId) {
        if (!isNotBlank(conversationId)) {
            throw new IllegalArgumentException("缺少 conversationId（请先调用 /api/diagnosis 获取）");
        }
        if (!isNotBlank(userMessage)) {
            throw new IllegalArgumentException("缺少 message 字段");
        }

        int msgLen = userMessage.length();
        log.info("[chat] start: conversationId={}, messageLen={}, projectId={}",
                conversationId, msgLen, projectId);

        tag("conversationId", conversationId);

        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);
        List<ChatMessage> messages = buildFollowUpMessages(conversationId, userMessage);
        ChatResponse response = reasoningLoop.callLlm(messages, activeToolRegistry, 0.3, 1024, null);

        // LLM 请求工具调用 → 暂存循环消息，返回待批准工具列表（不保存到对话历史）
        if (response.hasToolCalls()) {
            messages.add(ChatMessage.assistantToolCalls(response.toolCalls()));
            String sessionId = sessionStore.create(conversationId, projectId, currentTraceId(), messages);
            log.info("[chat] done: pendingToolCalls, conversationId={}, toolCalls={}",
                    conversationId, response.toolCalls().size());
            return ChatResult.pending(sessionId, response.toolCalls(), conversationId, currentTraceId(),
                    null, 1);
        }

        // 纯文本回复 → 保存到对话历史
        conversationService.save(conversationId, messages, ChatMessage.assistant(response.content()));
        log.info("[chat] done: reply, conversationId={}", conversationId);
        return ChatResult.reply(response.content(), conversationId, currentTraceId(), null, 0);
    }

    /**
     * 批准并执行工具调用，将结果回传 LLM 继续推理。
     */
    public ChatResult continueWithTools(String sessionId, List<ToolApproveRequest.ApprovedToolCall> approvedToolCalls) {
        ToolCallSessionStore.ToolCallSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("工具调用会话不存在或已过期: " + sessionId);
        }

        int approvedCount = approvedToolCalls == null ? 0 : approvedToolCalls.size();
        log.info("[continueWithTools] start: sessionId={}, approvedToolCalls={}", sessionId, approvedCount);

        String conversationId = session.conversationId();
        String projectId = session.projectId();
        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);
        List<ChatMessage> messages = new ArrayList<>(session.messages());

        // 执行用户批准的工具调用，收集结果并追加 tool 结果消息
        List<ChatResult.ExecutedTool> executedResults = new ArrayList<>();
        for (ToolApproveRequest.ApprovedToolCall call : approvedToolCalls) {
            String resultText = reasoningLoop.executeToolCall(activeToolRegistry, call.id(), call.name(), call.arguments());
            boolean success = !resultText.startsWith("ERROR:");
            executedResults.add(new ChatResult.ExecutedTool(call.id(), call.name(), call.arguments(), resultText, success));
            messages.add(ChatMessage.tool(call.id(), resultText));
        }

        // 回传工具结果，继续推理
        ChatResponse response = reasoningLoop.callLlm(messages, activeToolRegistry, 0.3, 1024, null);

        // LLM 又请求工具调用且未超过轮次上限 → 更新会话，返回新的待批准列表
        int roundCount = reasoningLoop.countToolRounds(messages);
        if (response.hasToolCalls() && roundCount < MAX_TOOL_ROUNDS) {
            messages.add(ChatMessage.assistantToolCalls(response.toolCalls()));
            sessionStore.update(sessionId, messages);
            log.info("[continueWithTools] continue loop: sessionId={}, round={}", sessionId, roundCount);
            return ChatResult.pending(sessionId, response.toolCalls(), conversationId, session.traceId(),
                    executedResults, roundCount);
        }

        // 循环结束 → 保存最终回复到对话历史，清理会话
        sessionStore.remove(sessionId);
        conversationService.save(conversationId, messages, ChatMessage.assistant(response.content()));
        log.info("[continueWithTools] loop ended: sessionId={}, rounds={}, conversationId={}",
                sessionId, roundCount, conversationId);
        return ChatResult.reply(response.content(), conversationId, session.traceId(),
                executedResults, roundCount);
    }

    // ========================================================================
    // 项目解析 + 对话历史加载
    // ========================================================================

    /** 解析项目专属工具注册表，失败时回退到全局注册表 */
    private ToolRegistry resolveToolRegistry(String projectId) {
        if (!isNotBlank(projectId)) {
            log.debug("[resolveToolRegistry] no projectId, using global registry");
            return toolRegistry;
        }
        try {
            ToolRegistry projectRegistry = projectManager.buildProjectToolRegistry(projectId);
            log.debug("[resolveToolRegistry] using project registry: projectId={}", projectId);
            return projectRegistry;
        } catch (Exception e) {
            log.debug("[resolveToolRegistry] fallback to global registry: projectId={}, error={}",
                    projectId, e.getMessage());
            return toolRegistry;
        }
    }

    /** 解析项目信息为 ProjectInfo（避免领域层依赖 ProjectEntity） */
    private ProjectInfo resolveProjectInfo(String projectId) {
        if (!isNotBlank(projectId)) return null;
        ProjectEntity project = projectManager.getProject(projectId).orElse(null);
        if (project == null) return null;
        return new ProjectInfo(project.getId(), project.getName(),
                project.getDescription(), project.getGitRepoLocalPath());
    }

    /** 加载对话历史消息（用于诊断上下文） */
    private List<ChatMessage> loadPriorMessages(String conversationId) {
        if (!isNotBlank(conversationId)) return List.of();
        List<ChatMessage> messages = new ArrayList<>();
        conversationService.loadHistory(conversationId, messages);
        return messages;
    }

    /** 构建追问消息列表（system + 历史 + user） */
    private List<ChatMessage> buildFollowUpMessages(String conversationId, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(promptRegistry.render("follow-up-system", Map.of())));
        conversationService.loadHistory(conversationId, messages);
        messages.add(ChatMessage.user(userMessage));
        return messages;
    }

    // ========================================================================
    // Span 辅助（null 安全）
    // ========================================================================

    private void tag(String key, String value) {
        Span current = tracer.currentSpan();
        if (current != null) current.tag(key, value);
    }

    private String currentTraceId() {
        Span current = tracer.currentSpan();
        return current != null ? current.context().traceId() : null;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    // ========================================================================
    // 结果载体（不序列化）
    // ========================================================================

    /** 诊断结果 */
    public record DiagnosisResult(DiagnosisReport report, String conversationId) {
    }

    /** 追问结果 — 可能是纯文本回复，也可能含待批准的工具调用 */
    public record ChatResult(
            String reply,
            String conversationId,
            String traceId,
            String sessionId,
            List<ToolCall> pendingToolCalls,
            List<ExecutedTool> executedToolResults,
            int toolRound,
            int maxToolRounds) {

        /** 已执行工具的结果（内部记录，映射为 ToolExecutionResultVo 返回前端） */
        public record ExecutedTool(String id, String name, String arguments, String result, boolean success) {
        }

        /** 纯文本回复（循环结束，可携带最后一轮的工具执行结果） */
        public static ChatResult reply(String reply, String conversationId, String traceId,
                                       List<ExecutedTool> executed, int round) {
            return new ChatResult(reply, conversationId, traceId, null, null, executed, round, MAX_TOOL_ROUNDS);
        }

        /** 待批准工具调用（循环继续，携带本轮刚执行的工具结果） */
        public static ChatResult pending(String sessionId, List<ToolCall> pendingToolCalls,
                                         String conversationId, String traceId,
                                         List<ExecutedTool> executed, int round) {
            return new ChatResult(null, conversationId, traceId, sessionId, pendingToolCalls, executed, round, MAX_TOOL_ROUNDS);
        }

        /** 是否有待批准的工具调用 */
        public boolean hasPendingToolCalls() {
            return pendingToolCalls != null && !pendingToolCalls.isEmpty();
        }
    }
}
