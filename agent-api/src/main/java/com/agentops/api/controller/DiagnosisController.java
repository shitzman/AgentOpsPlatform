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
     * 提交异常堆栈进行诊断（支持多源上下文注入和多轮对话）。
     *
     * <pre>
     * POST /api/diagnosis
     * {
     *   "stackTrace": "java.lang.NullPointerException: ...",
     *   "conversationId": "uuid-xxx",   // 可选，用于多轮对话
     *   "projectId": "uuid-xxx",         // 可选，启用多源上下文（环境+Git+日志）
     *   "logContent": "原始日志文本..."   // 可选，用于提取日志上下文
     * }
     * </pre>
     */
    @PostMapping("/diagnosis")
    public Map<String, Object> diagnose(@RequestBody Map<String, String> body) {
        String rawStackTrace = body.get("stackTrace");
        String conversationId = body.get("conversationId");
        String projectId = body.get("projectId");
        String logContent = body.get("logContent");

        if (rawStackTrace == null || rawStackTrace.isBlank()) {
            return Map.of("success", false, "error", "缺少 stackTrace 字段");
        }

        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);

        Span span = tracer.nextSpan().name("POST /api/diagnosis").start();
        try (var scope = tracer.withSpan(span)) {
            span.tag("conversationId", conversationId != null ? conversationId : "new");
            if (projectId != null) span.tag("projectId", projectId);

            // 1. 执行诊断工作流（解析堆栈 + 过滤项目代码）
            WorkflowContext context = new SimpleWorkflowContext();
            context.put(BusinessExceptionAgent.CTX_RAW_STACK_TRACE, rawStackTrace);
            WorkflowContext result = workflowEngine.execute(
                    BusinessExceptionAgent.WORKFLOW_NAME, context);
            StackTrace parsed = result.get(BusinessExceptionAgent.CTX_PARSED_TRACE, StackTrace.class);
            span.tag("exceptionType", parsed.exceptionType());

            // 2. 构建多源诊断上下文（Phase 3 增强）
            String enrichmentPrompt = "";
            if (projectId != null) {
                enrichmentPrompt = buildMultiSourceContext(projectId, parsed, logContent);
            }

            // 3. 渲染诊断 System Prompt（含多源上下文）
            String systemPrompt = promptRegistry.render("diagnosis-system", Map.of(
                    "exceptionType", parsed.exceptionType(),
                    "exceptionMessage", parsed.message() != null ? parsed.message() : "无",
                    "rawStackTrace", rawStackTrace
            ));
            systemPrompt = systemPrompt + enrichmentPrompt;

            // 4. 构建消息列表（含历史对话）
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPrompt));

            if (conversationId != null && !conversationId.isBlank()) {
                loadConversationHistory(conversationId, messages);
            }

            messages.add(ChatMessage.user("请对以上异常进行诊断分析，结合提供的所有上下文线索，只输出 JSON。"));

            // 5. 调用 LLM（带工具）
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

            // 6. 解析 JSON 为 DiagnosisReport（注入 traceId）
            String traceId = span.context().traceId();
            DiagnosisReport report = parseDiagnosisReport(response.content(), parsed.exceptionType(), traceId);

            // 7. 持久化诊断报告（Phase 3）
            saveDiagnosisReport(projectId, report, rawStackTrace);

            // 8. 保存对话记录
            String cid = saveConversation(conversationId, messages,
                    ChatMessage.assistant(response.content()));

            span.tag("report.severity", report.severity());
            span.tag("report.confidence", String.valueOf(report.confidence()));

            return Map.of("success", true, "report", report, "conversationId", cid);
        } catch (Exception e) {
            span.tag("error", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
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
    public Map<String, Object> listDiagnosis(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            QueryWrapper<DiagnosisReportEntity> wrapper = new QueryWrapper<>();
            if (projectId != null && !projectId.isBlank()) {
                wrapper.eq("project_id", projectId);
            }
            wrapper.orderByDesc("created_at");

            // 简易分页
            long total = diagnosisReportMapper.selectCount(wrapper);
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
    // 多源上下文构建 (Phase 3)
    // ========================================================================

    /**
     * 为诊断构建多源上下文注入文本。
     *
     * <p>收集：项目信息 + 运行环境 + Git 上下文（含 Blame）+ 日志上下文。
     */
    private String buildMultiSourceContext(String projectId, StackTrace parsed, String logContent) {
        ProjectEntity project = projectManager.getProject(projectId).orElse(null);
        if (project == null) return "";

        // 环境信息
        EnvironmentInfo env = EnvironmentCollector.collect();

        // Git 上下文（含项目代码帧的 Blame）
        GitContext gitCtx = null;
        String repoPath = project.getGitRepoLocalPath();
        if (repoPath != null && !repoPath.isBlank()) {
            GitContextProvider gitProvider = new GitContextProvider(repoPath);
            List<StackTraceFrame> projectFrames = parsed.frames().stream()
                    .filter(StackTraceFrame::isProjectCode)
                    .toList();
            List<GitContextProvider.FileLine> blameTargets = projectFrames.stream()
                    .filter(f -> f.fileName() != null && f.lineNumber() > 0)
                    .map(f -> GitContextProvider.FileLine.of(f.fileName(), f.lineNumber()))
                    .toList();
            gitCtx = gitProvider.collect(blameTargets);
        }

        // 日志上下文（从提供的 logContent 提取）
        String logCtx = null;
        if (logContent != null && !logContent.isBlank()) {
            logCtx = LogExtractor.extractLogContext(logContent, parsed.rawText());
        }

        // 组装 DiagnosisContext 并生成 Prompt 注入文本
        DiagnosisContext ctx = new DiagnosisContext(
                project.getId(), project.getName(), project.getDescription(),
                parsed.rawText(), parsed, logCtx, gitCtx, env);

        return "\n\n" + ctx.toPromptText();
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
