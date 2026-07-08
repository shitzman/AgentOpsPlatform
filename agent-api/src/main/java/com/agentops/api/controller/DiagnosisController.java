package com.agentops.api.controller;

import com.agentops.business.exceptionagent.BusinessExceptionAgent;
import com.agentops.business.exceptionagent.model.DiagnosisContext;
import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.business.exceptionagent.model.StackTrace;
import com.agentops.business.exceptionagent.model.StackTraceFrame;
import com.agentops.memory.MemoryEntry;
import com.agentops.memory.MemoryStore;
import com.agentops.prompts.PromptRegistry;
import com.agentops.repository.MySqlProjectManager;
import com.agentops.repository.entity.DiagnosisReportEntity;
import com.agentops.repository.entity.ProjectEntity;
import com.agentops.repository.mapper.DiagnosisReportMapper;
import com.agentops.runtime.model.ModelClient;
import com.agentops.runtime.model.ChatMessage;
import com.agentops.runtime.model.ChatRequest;
import com.agentops.runtime.model.ChatResponse;
import com.agentops.tools.*;
import com.agentops.workflow.SimpleWorkflowContext;
import com.agentops.workflow.WorkflowContext;
import com.agentops.workflow.WorkflowEngine;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 诊断 REST API — 支持多轮对话、工具调用和多源上下文关联诊断。
 *
 * <p>V1.0 Phase 3 增强：
 * <ul>
 *   <li>诊断时自动注入 Git Blame + 运行环境 + 日志上下文</li>
 *   <li>诊断报告持久化到 MySQL（diagnosis_reports 表）</li>
 *   <li>GET /api/diagnosis — 诊断历史分页查询</li>
 * </ul>
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/diagnosis — 提交诊断（多源上下文注入）</li>
 *   <li>GET  /api/diagnosis — 诊断历史查询</li>
 *   <li>POST /api/chat       — 多轮对话（追问、工具调用）</li>
 *   <li>GET  /api/health      — 健康检查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class DiagnosisController {

    private final WorkflowEngine workflowEngine;
    private final ModelClient modelClient;
    private final PromptRegistry promptRegistry;
    private final ToolRegistry toolRegistry;
    private final MemoryStore memoryStore;
    private final MySqlProjectManager projectManager;
    private final DiagnosisReportMapper diagnosisReportMapper;
    private final String modelName;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public DiagnosisController(WorkflowEngine workflowEngine,
                               ModelClient modelClient,
                               PromptRegistry promptRegistry,
                               ToolRegistry toolRegistry,
                               MemoryStore memoryStore,
                               MySqlProjectManager projectManager,
                               DiagnosisReportMapper diagnosisReportMapper,
                               @Value("${agentops.llm.model:deepseek-chat}") String modelName,
                               Tracer tracer) {
        this.workflowEngine = workflowEngine;
        this.modelClient = modelClient;
        this.promptRegistry = promptRegistry;
        this.toolRegistry = toolRegistry;
        this.memoryStore = memoryStore;
        this.projectManager = projectManager;
        this.diagnosisReportMapper = diagnosisReportMapper;
        this.modelName = modelName;
        this.objectMapper = new ObjectMapper();
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
    public Map<String, Object> diagnose(@RequestBody Map<String, String> body) {
        String rawStackTrace = body.get("stackTrace");
        String conversationId = body.get("conversationId");
        String projectId = body.get("projectId");
        String logContent = body.get("logContent");

        boolean hasStackTrace = rawStackTrace != null && !rawStackTrace.isBlank();
        boolean hasLogContent = logContent != null && !logContent.isBlank();

        // 至少需要提供堆栈或日志内容之一
        if (!hasStackTrace && !hasLogContent) {
            return Map.of("success", false, "error", "请提供异常堆栈（stackTrace）或日志内容（logContent）");
        }

        // 如果没有堆栈但有日志内容，尝试自动提取堆栈
        if (!hasStackTrace && hasLogContent) {
            String extracted = LogExtractor.extractStackTrace(logContent);
            if (extracted != null) {
                rawStackTrace = extracted;
                hasStackTrace = true;
            }
        }

        // 判定模式：有堆栈 → 堆栈诊断；仅日志 → 纯日志分析
        boolean isLogOnlyMode = !hasStackTrace;

        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);

        Span span = tracer.nextSpan().name("POST /api/diagnosis").start();
        try (var scope = tracer.withSpan(span)) {
            span.tag("mode", isLogOnlyMode ? "log-analysis" : "stack-trace");
            span.tag("conversationId", conversationId != null ? conversationId : "new");
            if (projectId != null) span.tag("projectId", projectId);

            if (isLogOnlyMode) {
                return executeLogOnlyDiagnosis(logContent, conversationId, projectId,
                        activeToolRegistry, span);
            } else {
                return executeStackTraceDiagnosis(rawStackTrace, logContent, conversationId,
                        projectId, activeToolRegistry, span);
            }
        } catch (Exception e) {
            span.tag("error", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        } finally {
            span.end();
        }
    }

    // ========================================================================
    // 堆栈诊断模式（原有流程）
    // ========================================================================

    private Map<String, Object> executeStackTraceDiagnosis(
            String rawStackTrace, String logContent, String conversationId,
            String projectId, ToolRegistry activeToolRegistry, Span span) {

        // 1. 执行诊断工作流（解析堆栈 + 过滤项目代码）
        WorkflowContext context = new SimpleWorkflowContext();
        context.put(BusinessExceptionAgent.CTX_RAW_STACK_TRACE, rawStackTrace);
        WorkflowContext result = workflowEngine.execute(
                BusinessExceptionAgent.WORKFLOW_NAME, context);
        StackTrace parsed = result.get(BusinessExceptionAgent.CTX_PARSED_TRACE, StackTrace.class);
        span.tag("exceptionType", parsed.exceptionType());

        // 2. 构建多源诊断上下文
        String enrichmentPrompt = "";
        if (projectId != null) {
            enrichmentPrompt = buildMultiSourceContext(projectId, parsed, logContent);
        }

        // 3. 渲染诊断 System Prompt
        String systemPrompt = promptRegistry.render("diagnosis-system", Map.of(
                "exceptionType", parsed.exceptionType(),
                "exceptionMessage", parsed.message() != null ? parsed.message() : "无",
                "rawStackTrace", rawStackTrace
        ));
        systemPrompt = systemPrompt + enrichmentPrompt;

        return finishDiagnosis(systemPrompt, conversationId, activeToolRegistry,
                parsed.exceptionType(), span, projectId, rawStackTrace);
    }

    // ========================================================================
    // 纯日志分析模式（v1.1 新增 — 无堆栈时的日志上下文诊断）
    // ========================================================================

    private Map<String, Object> executeLogOnlyDiagnosis(
            String logContent, String conversationId, String projectId,
            ToolRegistry activeToolRegistry, Span span) {

        // 1. 分析日志模式（错误/警告统计、样本提取）
        LogExtractor.LogAnalysis logAnalysis = LogExtractor.analyzeLogContent(logContent);

        // 2. 构建多源上下文（环境 + Git，无堆栈帧的 blame 定位）
        String enrichmentPrompt = "";
        if (projectId != null) {
            enrichmentPrompt = buildMultiSourceContextForLogOnly(projectId, logContent);
        }

        // 3. 渲染日志分析 System Prompt
        String systemPrompt = promptRegistry.render("log-analysis-system", Map.of(
                "logContent", logContent.length() > 8000
                        ? logContent.substring(0, 8000) + "\n... (日志过长，已截断至 8000 字符)" : logContent,
                "logAnalysis", logAnalysis.toPromptText()
        ));
        systemPrompt = systemPrompt + enrichmentPrompt;

        span.tag("logTotalLines", String.valueOf(logAnalysis.totalLines()));
        span.tag("logErrorCount", String.valueOf(logAnalysis.errorCount()));

        return finishDiagnosis(systemPrompt, conversationId, activeToolRegistry,
                "LogAnalysis", span, projectId, logContent);
    }

    // ========================================================================
    // 公共：调用 LLM + 解析 + 持久化 + 保存对话
    // ========================================================================

    private Map<String, Object> finishDiagnosis(
            String systemPrompt, String conversationId, ToolRegistry activeToolRegistry,
            String exceptionType, Span span, String projectId, String rawTrace) {

        // 构建消息列表（含历史对话）
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));

        if (conversationId != null && !conversationId.isBlank()) {
            loadConversationHistory(conversationId, messages);
        }

        messages.add(ChatMessage.user("请对以上内容进行诊断分析，结合提供的所有上下文线索，只输出 JSON。"));

        // 调用 LLM（带工具）
        Span llmSpan = tracer.nextSpan().name("llm.chat").start();
        ChatResponse response;
        try (var llmScope = tracer.withSpan(llmSpan)) {
            llmSpan.tag("model", modelName);
            ChatRequest request = new ChatRequest(
                    messages,
                    activeToolRegistry.listDefinitions(),
                    modelName,
                    0.2,
                    2048,
                    "json_object"
            );
            response = modelClient.chat(request);
            llmSpan.tag("finishReason", response.finishReason() != null ? response.finishReason() : "unknown");
        } finally {
            llmSpan.end();
        }

        // 解析 JSON 为 DiagnosisReport
        String traceId = span.context().traceId();
        DiagnosisReport report = parseDiagnosisReport(response.content(), exceptionType, traceId);

        // 持久化诊断报告
        saveDiagnosisReport(projectId, report, rawTrace);

        // 保存对话记录
        String cid = saveConversation(conversationId, messages,
                ChatMessage.assistant(response.content()));

        span.tag("report.severity", report.severity());
        span.tag("report.confidence", String.valueOf(report.confidence()));

        return Map.of("success", true, "report", report, "conversationId", cid);
    }

    /**
     * 诊断历史查询 — 按项目和分页查询历史诊断报告。
     *
     * <pre>
     * GET /api/diagnosis?projectId=X&page=0&size=20
     * </pre>
     */
    @GetMapping("/diagnosis")
    public Map<String, Object> listDiagnosis(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            QueryWrapper<DiagnosisReportEntity> wrapper = new QueryWrapper<>();
            if (projectId != null && !projectId.isBlank()) {
                wrapper.eq("project_id", projectId);
            }
            // 先查总数（不带 ORDER BY / LIMIT，H2 要求聚合查询不能有 ORDER BY）
            long total = diagnosisReportMapper.selectCount(wrapper);

            // 再加排序和分页
            wrapper.orderByDesc("created_at");
            wrapper.last("LIMIT " + (page * size) + "," + size);
            List<DiagnosisReportEntity> reports = diagnosisReportMapper.selectList(wrapper);

            return Map.of("success", true, "reports", reports,
                    "total", total, "page", page, "size", size);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 多轮对话端点 — 在已有诊断基础上追问。
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        String conversationId = body.get("conversationId");
        String userMessage = body.get("message");
        String projectId = body.get("projectId");

        if (conversationId == null || conversationId.isBlank()) {
            return Map.of("success", false, "error", "缺少 conversationId（请先调用 /api/diagnosis 获取）");
        }
        if (userMessage == null || userMessage.isBlank()) {
            return Map.of("success", false, "error", "缺少 message 字段");
        }

        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);

        Span span = tracer.nextSpan().name("POST /api/chat").start();
        try (var scope = tracer.withSpan(span)) {
            span.tag("conversationId", conversationId);

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(
                    "你是一名资深 SRE 和 Java 后端专家。用户正在就之前的异常诊断进行追问，" +
                    "请结合上下文给出专业、具体的回答。如果你需要查看代码仓库或日志，" +
                    "可以使用已注册的工具（git-log/git-blame/git-show/log-search）。"));
            loadConversationHistory(conversationId, messages);
            messages.add(ChatMessage.user(userMessage));

            Span llmSpan = tracer.nextSpan().name("llm.chat").start();
            ChatResponse response;
            try (var llmScope = tracer.withSpan(llmSpan)) {
                llmSpan.tag("model", modelName);
                ChatRequest request = new ChatRequest(
                        messages,
                        activeToolRegistry.listDefinitions(),
                        modelName,
                        0.3,
                        1024,
                        null
                );
                response = modelClient.chat(request);
            } finally {
                llmSpan.end();
            }

            saveConversation(conversationId, messages,
                    ChatMessage.assistant(response.content()));

            return Map.of(
                    "success", true,
                    "reply", response.content(),
                    "conversationId", conversationId,
                    "traceId", span.context().traceId()
            );
        } catch (Exception e) {
            span.tag("error", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        } finally {
            span.end();
        }
    }

    // ========================================================================
    // 多源上下文构建 (Phase 3 + v1.1 log-only)
    // ========================================================================

    /**
     * 为堆栈诊断构建多源上下文注入文本。
     */
    private String buildMultiSourceContext(String projectId, StackTrace parsed, String logContent) {
        ProjectEntity project = projectManager.getProject(projectId).orElse(null);
        if (project == null) return "";

        // 环境信息
        EnvironmentInfo env = EnvironmentCollector.collect();

        // Git 上下文（含项目代码帧的 Blame）
        GitContext gitCtx = collectGitContext(project, parsed != null ? parsed.frames() : List.of());

        // 日志上下文
        String logCtx = null;
        if (logContent != null && !logContent.isBlank() && parsed != null) {
            logCtx = LogExtractor.extractLogContext(logContent, parsed.rawText());
        }

        DiagnosisContext ctx = new DiagnosisContext(
                project.getId(), project.getName(), project.getDescription(),
                parsed != null ? parsed.rawText() : logContent, parsed, logCtx, gitCtx, env);

        return "\n\n" + ctx.toPromptText();
    }

    /**
     * 为纯日志分析模式构建多源上下文（无堆栈帧，不提取 blame 行号）。
     */
    private String buildMultiSourceContextForLogOnly(String projectId, String logContent) {
        ProjectEntity project = projectManager.getProject(projectId).orElse(null);
        if (project == null) return "";

        EnvironmentInfo env = EnvironmentCollector.collect();

        // Git 上下文（无 blame 目标，仅分支/最近提交）
        GitContext gitCtx = collectGitContext(project, List.of());

        // 日志上下文：取 logContent 首尾各 30 行作为样本
        String logCtx = extractLogSample(logContent, 30);

        DiagnosisContext ctx = new DiagnosisContext(
                project.getId(), project.getName(), project.getDescription(),
                logContent, null, logCtx, gitCtx, env);

        return "\n\n" + ctx.toPromptText();
    }

    /** 提取日志首尾样本行 */
    private String extractLogSample(String logContent, int sampleLines) {
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
    private GitContext collectGitContext(ProjectEntity project,
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
    // 诊断报告持久化 (Phase 3)
    // ========================================================================

    /** 将诊断报告保存到 MySQL diagnosis_reports 表 */
    private void saveDiagnosisReport(String projectId, DiagnosisReport report, String rawTrace) {
        try {
            DiagnosisReportEntity entity = new DiagnosisReportEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setProjectId(projectId);
            entity.setExceptionType(report.exceptionType());
            entity.setSummary(report.summary());
            entity.setRootCause(report.likelyRootCause());
            entity.setRelatedModules(objectMapper.writeValueAsString(report.relatedModules()));
            entity.setRecommendations(objectMapper.writeValueAsString(report.recommendations()));
            entity.setConfidence(report.confidence());
            entity.setSeverity(report.severity());
            entity.setImpactScope(report.impactScope());
            entity.setUrgency(report.urgency());
            entity.setTraceId(report.traceId());
            entity.setRawTrace(rawTrace);
            entity.setCreatedAt(LocalDateTime.now());
            diagnosisReportMapper.insert(entity);
        } catch (Exception ignored) {
            // 持久化失败不影响主诊断流程
        }
    }

    // ---- 工具注册表解析 ----

    private ToolRegistry resolveToolRegistry(String projectId) {
        if (projectId != null && !projectId.isBlank()) {
            try {
                return projectManager.buildProjectToolRegistry(projectId);
            } catch (Exception e) {
                return toolRegistry;
            }
        }
        return toolRegistry;
    }

    // ---- 对话历史管理 ----

    private void loadConversationHistory(String conversationId, List<ChatMessage> target) {
        List<MemoryEntry> history = memoryStore.findByType("conversation:" + conversationId, 20);
        for (int i = history.size() - 1; i >= 0; i--) {
            MemoryEntry entry = history.get(i);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = objectMapper.readValue(entry.content(), Map.class);
                target.add(new ChatMessage(
                        (String) msg.get("role"),
                        (String) msg.get("content"),
                        (String) msg.get("toolCallId"),
                        List.of()));
            } catch (JsonProcessingException ignored) {}
        }
    }

    private String saveConversation(String conversationId,
                                     List<ChatMessage> requestMessages,
                                     ChatMessage responseMessage) {
        String cid = conversationId != null && !conversationId.isBlank()
                ? conversationId : UUID.randomUUID().toString();

        try {
            if (requestMessages.size() > 1
                    && "system".equals(requestMessages.get(0).role())
                    && requestMessages.get(0).content() != null) {
                String type = "conversation:" + cid;
                if (memoryStore.findByType(type, 1).isEmpty()) {
                    Map<String, String> sysMsg = new HashMap<>();
                    sysMsg.put("role", "system");
                    sysMsg.put("content", requestMessages.get(0).content());
                    memoryStore.save(MemoryEntry.pending(type, objectMapper.writeValueAsString(sysMsg)));
                }
            }

            ChatMessage lastUser = null;
            for (int i = requestMessages.size() - 1; i >= 0; i--) {
                if ("user".equals(requestMessages.get(i).role())) {
                    lastUser = requestMessages.get(i);
                    break;
                }
            }

            if (lastUser != null && lastUser.content() != null) {
                Map<String, String> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", lastUser.content());
                memoryStore.save(MemoryEntry.pending("conversation:" + cid,
                        objectMapper.writeValueAsString(userMsg)));
            }

            if (responseMessage.content() != null) {
                Map<String, String> assistantMsg = new HashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", responseMessage.content());
                memoryStore.save(MemoryEntry.pending("conversation:" + cid,
                        objectMapper.writeValueAsString(assistantMsg)));
            }
        } catch (Exception ignored) {}

        return cid;
    }

    // ---- JSON 解析 ----

    private DiagnosisReport parseDiagnosisReport(String jsonContent, String exceptionType, String traceId) {
        try {
            String json = jsonContent.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf("\n");
                int end = json.lastIndexOf("```");
                if (start > 0 && end > start) {
                    json = json.substring(start, end).trim();
                }
            }
            DiagnosisReport parsed = objectMapper.readValue(json, DiagnosisReport.class);
            return new DiagnosisReport(
                    parsed.summary(), parsed.exceptionType(), parsed.severity(),
                    parsed.likelyRootCause(), parsed.impactScope(), parsed.urgency(),
                    parsed.relatedModules(), parsed.recommendations(),
                    parsed.confidence(), traceId,
                    parsed.gitBlameHints(), parsed.environmentFactors(),
                    parsed.logContextSummary());
        } catch (Exception e) {
            return new DiagnosisReport(
                    "JSON 解析失败，以下是原始诊断内容",
                    exceptionType,
                    "medium",
                    jsonContent,
                    "无法确定（解析失败）",
                    "计划修复",
                    List.of(),
                    List.of("请人工查看原始诊断内容"),
                    0.0,
                    traceId,
                    List.of(),
                    List.of(),
                    null
            );
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "1.0.0-SNAPSHOT",
                "prompts", promptRegistry.listNames().size() + " loaded",
                "tools", toolRegistry.listDefinitions().size() + " registered"
        );
    }
}
