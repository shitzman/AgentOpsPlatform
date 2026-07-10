package com.agentops.business.exceptionagent;

import com.agentops.business.exceptionagent.model.DiagnosisContext;
import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.business.exceptionagent.model.StackTrace;
import com.agentops.business.exceptionagent.model.StackTraceFrame;
import com.agentops.prompts.PromptRegistry;
import com.agentops.runtime.loop.ReasoningLoop;
import com.agentops.runtime.model.ChatMessage;
import com.agentops.tools.core.ToolRegistry;
import com.agentops.tools.env.EnvironmentCollector;
import com.agentops.tools.env.EnvironmentInfo;
import com.agentops.tools.git.GitContext;
import com.agentops.tools.git.GitContextProvider;
import com.agentops.tools.log.LogExtractor;
import com.agentops.workflow.SimpleWorkflowContext;
import com.agentops.workflow.WorkflowContext;
import com.agentops.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 诊断编排器 — 业务异常诊断的领域编排核心。
 *
 * <p>从 {@code agent-api/DiagnosisService} 下沉的领域逻辑，负责：
 * <ul>
 *   <li>诊断分支（堆栈模式 vs 纯日志模式）</li>
 *   <li>多源上下文构建（环境 + Git Blame + 日志上下文）</li>
 *   <li>System Prompt 渲染</li>
 *   <li>通过 {@link ReasoningLoop} 调用 LLM + 自动工具循环</li>
 *   <li>LLM 输出 JSON 解析为 {@link DiagnosisReport}</li>
 * </ul>
 *
 * <p>不负责：持久化、对话历史管理、HTTP 适配、tracing — 这些由 delivery 层处理。
 */
public class DiagnosisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisOrchestrator.class);

    private static final int LOG_TRUNCATE_LIMIT = 8000;
    private static final int MAX_TOOL_ROUNDS = 8;
    private static final double DIAGNOSIS_TEMPERATURE = 0.2;
    private static final int DIAGNOSIS_MAX_TOKENS = 2048;

    private final ReasoningLoop reasoningLoop;
    private final PromptRegistry promptRegistry;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DiagnosisOrchestrator(ReasoningLoop reasoningLoop,
                                 PromptRegistry promptRegistry,
                                 WorkflowEngine workflowEngine) {
        this.reasoningLoop = reasoningLoop;
        this.promptRegistry = promptRegistry;
        this.workflowEngine = workflowEngine;
    }

    /**
     * 执行诊断（堆栈模式或纯日志模式）。
     *
     * @param rawStackTrace    原始堆栈文本（可为 null）
     * @param logContent       日志内容（可为 null）
     * @param projectInfo      项目信息（可为 null，表示无项目上下文）
     * @param activeToolRegistry 可用工具注册表
     * @param priorMessages    对话历史消息（可为空列表）
     * @param traceId          当前 trace ID（用于报告关联）
     * @return 诊断结果
     */
    public DiagnosisOutcome diagnose(String rawStackTrace, String logContent,
                                    ProjectInfo projectInfo, ToolRegistry activeToolRegistry,
                                    List<ChatMessage> priorMessages, String traceId) {
        boolean hasStackTrace = isNotBlank(rawStackTrace);

        if (hasStackTrace) {
            log.info("[diagnose] branch: stack-trace");
            return executeStackTraceDiagnosis(rawStackTrace, logContent, projectInfo,
                    activeToolRegistry, priorMessages, traceId);
        }
        log.info("[diagnose] branch: log-analysis");
        return executeLogOnlyDiagnosis(logContent, projectInfo, activeToolRegistry, priorMessages, traceId);
    }

    // ========================================================================
    // 堆栈诊断模式
    // ========================================================================

    private DiagnosisOutcome executeStackTraceDiagnosis(String rawStackTrace, String logContent,
                                                        ProjectInfo projectInfo,
                                                        ToolRegistry activeToolRegistry,
                                                        List<ChatMessage> priorMessages,
                                                        String traceId) {
        StackTrace parsed = parseStackTrace(rawStackTrace);
        log.debug("[executeStackTraceDiagnosis] exceptionType={}, frames={}",
                parsed.exceptionType(), parsed.frames() != null ? parsed.frames().size() : 0);

        String enrichmentPrompt = projectInfo != null
                ? buildMultiSourceContext(projectInfo, parsed, logContent) : "";

        String systemPrompt = promptRegistry.render("diagnosis-system", Map.of(
                "exceptionType", parsed.exceptionType(),
                "exceptionMessage", parsed.message() != null ? parsed.message() : "无",
                "rawStackTrace", rawStackTrace
        )) + enrichmentPrompt;

        return finishDiagnosis(systemPrompt, activeToolRegistry, priorMessages,
                parsed.exceptionType(), traceId);
    }

    private StackTrace parseStackTrace(String rawStackTrace) {
        WorkflowContext context = new SimpleWorkflowContext();
        context.put(BusinessExceptionAgent.CTX_RAW_STACK_TRACE, rawStackTrace);
        WorkflowContext result = workflowEngine.execute(BusinessExceptionAgent.WORKFLOW_NAME, context);
        return result.get(BusinessExceptionAgent.CTX_PARSED_TRACE, StackTrace.class);
    }

    // ========================================================================
    // 纯日志分析模式
    // ========================================================================

    private DiagnosisOutcome executeLogOnlyDiagnosis(String logContent, ProjectInfo projectInfo,
                                                     ToolRegistry activeToolRegistry,
                                                     List<ChatMessage> priorMessages,
                                                     String traceId) {
        LogExtractor.LogAnalysis logAnalysis = LogExtractor.analyzeLogContent(logContent);
        log.debug("[executeLogOnlyDiagnosis] logTotalLines={}, logErrorCount={}",
                logAnalysis.totalLines(), logAnalysis.errorCount());

        String enrichmentPrompt = projectInfo != null
                ? buildMultiSourceContextForLogOnly(projectInfo, logContent) : "";

        String systemPrompt = promptRegistry.render("log-analysis-system", Map.of(
                "logContent", truncateLog(logContent),
                "logAnalysis", logAnalysis.toPromptText()
        )) + enrichmentPrompt;

        return finishDiagnosis(systemPrompt, activeToolRegistry, priorMessages,
                "LogAnalysis", traceId);
    }

    private static String truncateLog(String logContent) {
        if (logContent.length() <= LOG_TRUNCATE_LIMIT) return logContent;
        return logContent.substring(0, LOG_TRUNCATE_LIMIT)
                + "\n... (日志过长，已截断至 " + LOG_TRUNCATE_LIMIT + " 字符)";
    }

    // ========================================================================
    // 公共：调用 LLM + 解析报告
    // ========================================================================

    private DiagnosisOutcome finishDiagnosis(String systemPrompt, ToolRegistry activeToolRegistry,
                                             List<ChatMessage> priorMessages,
                                             String exceptionType, String traceId) {
        List<ChatMessage> messages = buildDiagnosisMessages(systemPrompt, priorMessages);
        log.debug("[finishDiagnosis] messages={}, exceptionType={}", messages.size(), exceptionType);
        String content = reasoningLoop.runWithAutoToolLoop(messages, activeToolRegistry,
                DIAGNOSIS_TEMPERATURE, DIAGNOSIS_MAX_TOKENS, null, MAX_TOOL_ROUNDS);

        DiagnosisReport report = parseDiagnosisReport(content, exceptionType, traceId);
        log.info("[finishDiagnosis] done: traceId={}, exceptionType={}, severity={}, confidence={}",
                traceId, exceptionType, report.severity(), report.confidence());

        return new DiagnosisOutcome(report, content, messages);
    }

    private List<ChatMessage> buildDiagnosisMessages(String systemPrompt, List<ChatMessage> priorMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (priorMessages != null) {
            messages.addAll(priorMessages);
        }
        messages.add(ChatMessage.user("请对以上内容进行诊断分析，结合提供的所有上下文线索，只输出 JSON。"));
        return messages;
    }

    // ========================================================================
    // 多源上下文构建
    // ========================================================================

    private String buildMultiSourceContext(ProjectInfo project, StackTrace parsed, String logContent) {
        EnvironmentInfo env = EnvironmentCollector.collect();
        GitContext gitCtx = collectGitContext(project.repoPath(),
                parsed != null ? parsed.frames() : List.of());

        String logCtx = null;
        if (isNotBlank(logContent) && parsed != null) {
            logCtx = LogExtractor.extractLogContext(logContent, parsed.rawText());
        }

        DiagnosisContext ctx = new DiagnosisContext(
                project.id(), project.name(), project.description(),
                parsed != null ? parsed.rawText() : logContent, parsed, logCtx, gitCtx, env);
        log.debug("[buildMultiSourceContext] projectId={}, env={}, gitCtx={}, logCtx={}",
                project.id(), env != null, gitCtx != null, logCtx != null);
        return "\n\n" + ctx.toPromptText();
    }

    private String buildMultiSourceContextForLogOnly(ProjectInfo project, String logContent) {
        EnvironmentInfo env = EnvironmentCollector.collect();
        GitContext gitCtx = collectGitContext(project.repoPath(), List.of());
        String logCtx = extractLogSample(logContent, 30);

        DiagnosisContext ctx = new DiagnosisContext(
                project.id(), project.name(), project.description(),
                logContent, null, logCtx, gitCtx, env);
        log.debug("[buildMultiSourceContextForLogOnly] projectId={}, env={}, gitCtx={}, logCtx={}",
                project.id(), env != null, gitCtx != null, logCtx != null);
        return "\n\n" + ctx.toPromptText();
    }

    private static String extractLogSample(String logContent, int sampleLines) {
        if (logContent == null || logContent.isBlank()) return null;
        String[] lines = logContent.split("\\r?\\n");
        if (lines.length <= sampleLines * 2) return logContent;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sampleLines && i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("... (省略 ").append(lines.length - sampleLines * 2).append(" 行) ...\n");
        for (int i = Math.max(sampleLines, lines.length - sampleLines); i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().trim();
    }

    private static GitContext collectGitContext(String repoPath, List<StackTraceFrame> projectFrames) {
        if (repoPath == null || repoPath.isBlank()) return null;

        GitContextProvider gitProvider = new GitContextProvider(repoPath);
        List<GitContextProvider.FileLine> blameTargets = projectFrames.stream()
                .filter(f -> f.fileName() != null && f.lineNumber() > 0)
                .map(f -> GitContextProvider.FileLine.of(f.fileName(), f.lineNumber()))
                .toList();
        return gitProvider.collect(blameTargets);
    }

    // ========================================================================
    // JSON 解析
    // ========================================================================

    private DiagnosisReport parseDiagnosisReport(String jsonContent, String exceptionType, String traceId) {
        try {
            String json = stripMarkdownFence(jsonContent);
            DiagnosisReport parsed = objectMapper.readValue(json, DiagnosisReport.class);
            return new DiagnosisReport(
                    parsed.summary(), parsed.exceptionType(), parsed.severity(),
                    parsed.likelyRootCause(), parsed.impactScope(), parsed.urgency(),
                    parsed.relatedModules(), parsed.recommendations(),
                    parsed.confidence(), traceId,
                    parsed.gitBlameHints(), parsed.environmentFactors(),
                    parsed.logContextSummary(), parsed.followUpQuestions());
        } catch (Exception e) {
            log.warn("[parseDiagnosisReport] JSON parse failed, using fallback: exceptionType={}, error={}",
                    exceptionType, e.getMessage());
            return fallbackReport(jsonContent, exceptionType, traceId);
        }
    }

    private static String stripMarkdownFence(String json) {
        String trimmed = json.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int start = trimmed.indexOf("\n");
        int end = trimmed.lastIndexOf("```");
        if (start > 0 && end > start) {
            return trimmed.substring(start, end).trim();
        }
        return trimmed;
    }

    private static DiagnosisReport fallbackReport(String rawContent, String exceptionType, String traceId) {
        return new DiagnosisReport(
                "JSON 解析失败，以下是原始诊断内容",
                exceptionType,
                "medium",
                rawContent,
                "无法确定（解析失败）",
                "计划修复",
                List.of(),
                List.of("请人工查看原始诊断内容"),
                0.0,
                traceId,
                List.of(),
                List.of(),
                null,
                List.of());
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
