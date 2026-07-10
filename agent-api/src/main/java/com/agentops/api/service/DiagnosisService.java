package com.agentops.api.service;

import com.agentops.api.dto.ToolApproveRequest;
import com.agentops.business.exceptionagent.BusinessExceptionAgent;
import com.agentops.business.exceptionagent.model.DiagnosisContext;
import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.business.exceptionagent.model.StackTrace;
import com.agentops.business.exceptionagent.model.StackTraceFrame;
import com.agentops.prompts.PromptRegistry;
import com.agentops.repository.MySqlProjectManager;
import com.agentops.repository.entity.ProjectEntity;
import com.agentops.runtime.model.ChatMessage;
import com.agentops.runtime.model.ChatRequest;
import com.agentops.runtime.model.ChatResponse;
import com.agentops.runtime.model.ModelClient;
import com.agentops.runtime.model.ToolCall;
import com.agentops.tools.core.ToolExecutor;
import com.agentops.tools.core.ToolRegistry;
import com.agentops.tools.core.ToolResult;
import com.agentops.tools.env.EnvironmentCollector;
import com.agentops.tools.env.EnvironmentInfo;
import com.agentops.tools.git.GitContext;
import com.agentops.tools.git.GitContextProvider;
import com.agentops.tools.log.LogExtractor;
import com.agentops.workflow.SimpleWorkflowContext;
import com.agentops.workflow.WorkflowContext;
import com.agentops.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 诊断编排服务 — 从 {@code DiagnosisController} 抽取的核心业务逻辑。
 *
 * <p>负责：解析堆栈/日志、构建多源上下文、渲染 System Prompt、调用 LLM、
 * 解析结构化报告、持久化报告与对话历史。
 *
 * <p>HTTP 级 Span 由 Controller 创建并激活，本服务通过 {@link Tracer#currentSpan()}
 * 访问当前 Span 进行打标，并创建 {@code llm.chat} 子 Span。所有 Span 访问均做 null 检查以支持单测。
 */
@Service
public class DiagnosisService {

    private static final int LOG_TRUNCATE_LIMIT = 8000;
    private static final int MAX_TOOL_ROUNDS = 8;
    private static final String FOLLOW_UP_SYSTEM_PROMPT =
            "你是一名资深 SRE 和 Java 后端专家。用户正在就之前的异常诊断进行追问，" +
            "请结合上下文给出专业、具体的回答。如果你需要查看代码仓库或日志，" +
            "可以使用已注册的工具（git-log/git-blame/git-show/log-search）。";

    private final WorkflowEngine workflowEngine;
    private final ModelClient modelClient;
    private final PromptRegistry promptRegistry;
    private final ToolRegistry toolRegistry;
    private final MySqlProjectManager projectManager;
    private final ConversationService conversationService;
    private final DiagnosisReportPersistenceService reportPersistenceService;
    private final ToolCallSessionStore sessionStore;
    private final Tracer tracer;
    private final String modelName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DiagnosisService(WorkflowEngine workflowEngine,
                            ModelClient modelClient,
                            PromptRegistry promptRegistry,
                            ToolRegistry toolRegistry,
                            MySqlProjectManager projectManager,
                            ConversationService conversationService,
                            DiagnosisReportPersistenceService reportPersistenceService,
                            ToolCallSessionStore sessionStore,
                            Tracer tracer,
                            @Value("${agentops.llm.model:deepseek-chat}") String modelName) {
        this.workflowEngine = workflowEngine;
        this.modelClient = modelClient;
        this.promptRegistry = promptRegistry;
        this.toolRegistry = toolRegistry;
        this.projectManager = projectManager;
        this.conversationService = conversationService;
        this.reportPersistenceService = reportPersistenceService;
        this.sessionStore = sessionStore;
        this.tracer = tracer;
        this.modelName = modelName;
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

        tag("mode", hasStackTrace ? "stack-trace" : "log-analysis");
        tag("conversationId", conversationId != null ? conversationId : "new");
        if (projectId != null) tag("projectId", projectId);

        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);
        if (!hasStackTrace) {
            return executeLogOnlyDiagnosis(logContent, conversationId, projectId, activeToolRegistry);
        }
        return executeStackTraceDiagnosis(rawStackTrace, logContent, conversationId, projectId, activeToolRegistry);
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

        tag("conversationId", conversationId);

        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);
        List<ChatMessage> messages = buildFollowUpMessages(conversationId, userMessage);
        ChatResponse response = callLlm(messages, activeToolRegistry, 0.3, 1024, null);

        // LLM 请求工具调用 → 暂存循环消息，返回待批准工具列表（不保存到对话历史）
        if (response.hasToolCalls()) {
            messages.add(ChatMessage.assistantToolCalls(response.toolCalls()));
            String sessionId = sessionStore.create(conversationId, projectId, currentTraceId(), messages);
            return ChatResult.pending(sessionId, response.toolCalls(), conversationId, currentTraceId(),
                    null, 1);
        }

        // 纯文本回复 → 保存到对话历史（同原逻辑）
        conversationService.save(conversationId, messages, ChatMessage.assistant(response.content()));
        return ChatResult.reply(response.content(), conversationId, currentTraceId(), null, 0);
    }

    /**
     * 批准并执行工具调用，将结果回传 LLM 继续推理。
     *
     * <p>循环逻辑：执行用户批准的工具 → 构造 tool 结果消息 → 追加到循环消息列表 →
     * 再次调用 LLM。若 LLM 又请求工具调用且未超过轮次上限，返回新的待批准列表；
     * 否则保存最终回复到对话历史并结束循环。
     *
     * @param sessionId        工具调用循环会话 ID
     * @param approvedToolCalls 用户批准的工具调用列表（参数可被修改）
     * @return 追问结果（可能含新的待批准工具调用，或最终文本回复）
     */
    public ChatResult continueWithTools(String sessionId, List<ToolApproveRequest.ApprovedToolCall> approvedToolCalls) {
        ToolCallSessionStore.ToolCallSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("工具调用会话不存在或已过期: " + sessionId);
        }

        String conversationId = session.conversationId();
        String projectId = session.projectId();
        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);
        List<ChatMessage> messages = new ArrayList<>(session.messages());

        // 执行用户批准的工具调用，收集结果并追加 tool 结果消息
        List<ChatResult.ExecutedTool> executedResults = new ArrayList<>();
        for (ToolApproveRequest.ApprovedToolCall call : approvedToolCalls) {
            String resultText = executeToolCall(activeToolRegistry, call);
            boolean success = !resultText.startsWith("ERROR:");
            executedResults.add(new ChatResult.ExecutedTool(call.id(), call.name(), call.arguments(), resultText, success));
            messages.add(ChatMessage.tool(call.id(), resultText));
        }

        // 回传工具结果，继续推理
        ChatResponse response = callLlm(messages, activeToolRegistry, 0.3, 1024, null);

        // LLM 又请求工具调用且未超过轮次上限 → 更新会话，返回新的待批准列表
        int roundCount = countToolRounds(messages);
        if (response.hasToolCalls() && roundCount < MAX_TOOL_ROUNDS) {
            messages.add(ChatMessage.assistantToolCalls(response.toolCalls()));
            sessionStore.update(sessionId, messages);
            return ChatResult.pending(sessionId, response.toolCalls(), conversationId, session.traceId(),
                    executedResults, roundCount);
        }

        // 循环结束 → 保存最终回复到对话历史，清理会话
        sessionStore.remove(sessionId);
        conversationService.save(conversationId, messages, ChatMessage.assistant(response.content()));
        return ChatResult.reply(response.content(), conversationId, session.traceId(),
                executedResults, roundCount);
    }

    /** 执行单个工具调用，返回结果文本（成功取 output，失败取 error） */
    private String executeToolCall(ToolRegistry registry, ToolApproveRequest.ApprovedToolCall call) {
        Optional<ToolExecutor> executorOpt = registry.getExecutor(call.name());
        if (executorOpt.isEmpty()) {
            return "ERROR: 未知工具 " + call.name();
        }
        try {
            Map<String, Object> args = parseToolArguments(call.arguments());
            ToolResult result = executorOpt.get().execute(args);
            return result.success() ? result.output() : "ERROR: " + result.error();
        } catch (Exception e) {
            return "ERROR: 工具执行失败 " + call.name() + " — " + e.getMessage();
        }
    }

    /** 执行单个工具调用（ToolCall 重载，用于诊断流程中自动执行 LLM 请求的工具） */
    private String executeToolCall(ToolRegistry registry, ToolCall call) {
        Optional<ToolExecutor> executorOpt = registry.getExecutor(call.name());
        if (executorOpt.isEmpty()) {
            return "ERROR: 未知工具 " + call.name();
        }
        try {
            Map<String, Object> args = parseToolArguments(call.arguments());
            ToolResult result = executorOpt.get().execute(args);
            return result.success() ? result.output() : "ERROR: " + result.error();
        } catch (Exception e) {
            return "ERROR: 工具执行失败 " + call.name() + " — " + e.getMessage();
        }
    }

    /** 将工具参数 JSON 字符串解析为 Map，空字符串视为无参数 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(arguments, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("工具参数 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /** 统计消息列表中的工具调用轮次（assistant tool_calls 消息数） */
    private static int countToolRounds(List<ChatMessage> messages) {
        int count = 0;
        for (ChatMessage msg : messages) {
            if ("assistant".equals(msg.role()) && !msg.toolCalls().isEmpty()) count++;
        }
        return count;
    }

    // ========================================================================
    // 堆栈诊断模式
    // ========================================================================

    /** 解析堆栈 + 构建多源上下文 + 渲染 diagnosis-system Prompt */
    private DiagnosisResult executeStackTraceDiagnosis(String rawStackTrace, String logContent,
                                                       String conversationId, String projectId,
                                                       ToolRegistry activeToolRegistry) {
        StackTrace parsed = parseStackTrace(rawStackTrace);
        tag("exceptionType", parsed.exceptionType());

        String enrichmentPrompt = projectId != null
                ? buildMultiSourceContext(projectId, parsed, logContent) : "";

        String systemPrompt = promptRegistry.render("diagnosis-system", Map.of(
                "exceptionType", parsed.exceptionType(),
                "exceptionMessage", parsed.message() != null ? parsed.message() : "无",
                "rawStackTrace", rawStackTrace
        )) + enrichmentPrompt;

        return finishDiagnosis(systemPrompt, conversationId, activeToolRegistry,
                parsed.exceptionType(), projectId, rawStackTrace);
    }

    /** 执行诊断工作流，解析原始堆栈为结构化 StackTrace */
    private StackTrace parseStackTrace(String rawStackTrace) {
        WorkflowContext context = new SimpleWorkflowContext();
        context.put(BusinessExceptionAgent.CTX_RAW_STACK_TRACE, rawStackTrace);
        WorkflowContext result = workflowEngine.execute(BusinessExceptionAgent.WORKFLOW_NAME, context);
        return result.get(BusinessExceptionAgent.CTX_PARSED_TRACE, StackTrace.class);
    }

    // ========================================================================
    // 纯日志分析模式
    // ========================================================================

    /** 分析日志模式 + 构建多源上下文 + 渲染 log-analysis-system Prompt */
    private DiagnosisResult executeLogOnlyDiagnosis(String logContent, String conversationId,
                                                    String projectId, ToolRegistry activeToolRegistry) {
        LogExtractor.LogAnalysis logAnalysis = LogExtractor.analyzeLogContent(logContent);

        String enrichmentPrompt = projectId != null
                ? buildMultiSourceContextForLogOnly(projectId, logContent) : "";

        String systemPrompt = promptRegistry.render("log-analysis-system", Map.of(
                "logContent", truncateLog(logContent),
                "logAnalysis", logAnalysis.toPromptText()
        )) + enrichmentPrompt;

        tag("logTotalLines", String.valueOf(logAnalysis.totalLines()));
        tag("logErrorCount", String.valueOf(logAnalysis.errorCount()));

        return finishDiagnosis(systemPrompt, conversationId, activeToolRegistry,
                "LogAnalysis", projectId, logContent);
    }

    /** 日志过长时截断至 {@value #LOG_TRUNCATE_LIMIT} 字符 */
    private static String truncateLog(String logContent) {
        if (logContent.length() <= LOG_TRUNCATE_LIMIT) return logContent;
        return logContent.substring(0, LOG_TRUNCATE_LIMIT)
                + "\n... (日志过长，已截断至 " + LOG_TRUNCATE_LIMIT + " 字符)";
    }

    // ========================================================================
    // 公共：调用 LLM + 解析 + 持久化 + 保存对话
    // ========================================================================

    /** 构建消息、调用 LLM（含工具调用循环）、解析报告、持久化、保存对话 */
    private DiagnosisResult finishDiagnosis(String systemPrompt, String conversationId,
                                            ToolRegistry activeToolRegistry, String exceptionType,
                                            String projectId, String rawTrace) {
        List<ChatMessage> messages = buildDiagnosisMessages(systemPrompt, conversationId);
        String content = runDiagnosisWithToolLoop(messages, activeToolRegistry);

        String traceId = currentTraceId();
        DiagnosisReport report = parseDiagnosisReport(content, exceptionType, traceId);

        reportPersistenceService.saveReport(projectId, report, rawTrace);
        String cid = conversationService.save(conversationId, messages,
                ChatMessage.assistant(content));

        tag("report.severity", report.severity());
        tag("report.confidence", String.valueOf(report.confidence()));

        return new DiagnosisResult(report, cid);
    }

    /**
     * 诊断工具调用循环 — LLM 可在生成最终 JSON 报告前请求调用 git 等工具分析代码。
     *
     * <p>与追问场景的 {@code continueWithTools} 不同，诊断阶段的工具调用是自动执行的
     * （无需用户批准），因为这是初始分析流程，用户已在项目配置中启用了相应工具。
     *
     * <p>循环逻辑：LLM 请求工具 → 自动执行 → 结果回传 → LLM 继续分析。
     * 直到 LLM 返回纯文本（JSON 报告）或达到轮次上限。
     */
    private String runDiagnosisWithToolLoop(List<ChatMessage> messages, ToolRegistry activeToolRegistry) {
        // 首次调用不限制 responseFormat，允许 LLM 自由选择工具调用或直接输出 JSON
        ChatResponse response = callLlm(messages, activeToolRegistry, 0.2, 2048, null);

        int round = 0;
        while (response.hasToolCalls() && round < MAX_TOOL_ROUNDS) {
            // 追加 assistant 的 tool_calls 消息
            messages.add(ChatMessage.assistantToolCalls(response.toolCalls()));

            // 自动执行所有请求的工具（诊断阶段无需用户批准）
            for (ToolCall call : response.toolCalls()) {
                String resultText = executeToolCall(activeToolRegistry, call);
                messages.add(ChatMessage.tool(call.id(), resultText));
            }

            round++;
            tag("diagnosis.toolRound", String.valueOf(round));

            // 继续调用 LLM，可能请求更多工具或输出最终 JSON
            response = callLlm(messages, activeToolRegistry, 0.2, 2048, null);
        }

        return response.content();
    }

    /** 构建诊断消息列表（system + 历史 + user） */
    private List<ChatMessage> buildDiagnosisMessages(String systemPrompt, String conversationId) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (isNotBlank(conversationId)) {
            conversationService.loadHistory(conversationId, messages);
        }
        messages.add(ChatMessage.user("请对以上内容进行诊断分析，结合提供的所有上下文线索，只输出 JSON。"));
        return messages;
    }

    /** 构建追问消息列表（system + 历史 + user） */
    private List<ChatMessage> buildFollowUpMessages(String conversationId, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(FOLLOW_UP_SYSTEM_PROMPT));
        conversationService.loadHistory(conversationId, messages);
        messages.add(ChatMessage.user(userMessage));
        return messages;
    }

    /** 调用 LLM（创建 llm.chat 子 Span） */
    private ChatResponse callLlm(List<ChatMessage> messages, ToolRegistry activeToolRegistry,
                                 double temperature, int maxTokens, String responseFormat) {
        Span llmSpan = tracer.nextSpan().name("llm.chat").start();
        try (var scope = tracer.withSpan(llmSpan)) {
            llmSpan.tag("model", modelName);
            ChatRequest request = new ChatRequest(
                    messages,
                    activeToolRegistry.listDefinitions(),
                    modelName,
                    temperature,
                    maxTokens,
                    responseFormat);
            ChatResponse response = modelClient.chat(request);
            llmSpan.tag("finishReason", response.finishReason() != null ? response.finishReason() : "unknown");
            return response;
        } finally {
            llmSpan.end();
        }
    }

    // ========================================================================
    // 多源上下文构建
    // ========================================================================

    /** 为堆栈诊断构建多源上下文（环境 + Git Blame + 日志上下文） */
    private String buildMultiSourceContext(String projectId, StackTrace parsed, String logContent) {
        ProjectEntity project = projectManager.getProject(projectId).orElse(null);
        if (project == null) return "";

        EnvironmentInfo env = EnvironmentCollector.collect();
        GitContext gitCtx = collectGitContext(project, parsed != null ? parsed.frames() : List.of());

        String logCtx = null;
        if (isNotBlank(logContent) && parsed != null) {
            logCtx = LogExtractor.extractLogContext(logContent, parsed.rawText());
        }

        DiagnosisContext ctx = new DiagnosisContext(
                project.getId(), project.getName(), project.getDescription(),
                parsed != null ? parsed.rawText() : logContent, parsed, logCtx, gitCtx, env);
        return "\n\n" + ctx.toPromptText();
    }

    /** 为纯日志分析构建多源上下文（环境 + Git，不提取 blame） */
    private String buildMultiSourceContextForLogOnly(String projectId, String logContent) {
        ProjectEntity project = projectManager.getProject(projectId).orElse(null);
        if (project == null) return "";

        EnvironmentInfo env = EnvironmentCollector.collect();
        GitContext gitCtx = collectGitContext(project, List.of());
        String logCtx = extractLogSample(logContent, 30);

        DiagnosisContext ctx = new DiagnosisContext(
                project.getId(), project.getName(), project.getDescription(),
                logContent, null, logCtx, gitCtx, env);
        return "\n\n" + ctx.toPromptText();
    }

    /** 提取日志首尾样本行 */
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

    /** 收集 Git 上下文（含可选的 Blame 目标列表） */
    private static GitContext collectGitContext(ProjectEntity project,
                                                List<StackTraceFrame> projectFrames) {
        String repoPath = project.getGitRepoLocalPath();
        if (repoPath == null || repoPath.isBlank()) return null;

        GitContextProvider gitProvider = new GitContextProvider(repoPath);
        List<GitContextProvider.FileLine> blameTargets = projectFrames.stream()
                .filter(f -> f.fileName() != null && f.lineNumber() > 0)
                .map(f -> GitContextProvider.FileLine.of(f.fileName(), f.lineNumber()))
                .toList();
        return gitProvider.collect(blameTargets);
    }

    // ========================================================================
    // 工具注册表解析
    // ========================================================================

    /** 解析项目专属工具注册表，失败时回退到全局注册表 */
    private ToolRegistry resolveToolRegistry(String projectId) {
        if (!isNotBlank(projectId)) return toolRegistry;
        try {
            return projectManager.buildProjectToolRegistry(projectId);
        } catch (Exception e) {
            return toolRegistry;
        }
    }

    // ========================================================================
    // JSON 解析
    // ========================================================================

    /** 解析 LLM 输出的 JSON 为 DiagnosisReport，失败时返回降级报告 */
    private DiagnosisReport parseDiagnosisReport(String jsonContent, String exceptionType, String traceId) {
        try {
            String json = stripMarkdownFence(jsonContent);
            DiagnosisReport parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, DiagnosisReport.class);
            return new DiagnosisReport(
                    parsed.summary(), parsed.exceptionType(), parsed.severity(),
                    parsed.likelyRootCause(), parsed.impactScope(), parsed.urgency(),
                    parsed.relatedModules(), parsed.recommendations(),
                    parsed.confidence(), traceId,
                    parsed.gitBlameHints(), parsed.environmentFactors(),
                    parsed.logContextSummary(), parsed.followUpQuestions());
        } catch (Exception e) {
            return fallbackReport(jsonContent, exceptionType, traceId);
        }
    }

    /** 去除 ```json ... ``` Markdown 代码块围栏 */
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

    /** JSON 解析失败时的降级报告 */
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
