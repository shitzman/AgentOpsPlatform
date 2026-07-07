package com.agentops.api.controller;

import com.agentops.business.exceptionagent.BusinessExceptionAgent;
import com.agentops.business.exceptionagent.ProjectManager;
import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.business.exceptionagent.model.Project;
import com.agentops.business.exceptionagent.model.StackTrace;
import com.agentops.memory.MemoryEntry;
import com.agentops.memory.MemoryStore;
import com.agentops.prompts.PromptRegistry;
import com.agentops.runtime.model.ModelClient;
import com.agentops.runtime.model.ChatMessage;
import com.agentops.runtime.model.ChatRequest;
import com.agentops.runtime.model.ChatResponse;
import com.agentops.tools.ToolRegistry;
import com.agentops.workflow.SimpleWorkflowContext;
import com.agentops.workflow.WorkflowContext;
import com.agentops.workflow.WorkflowEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 诊断 REST API — 支持多轮对话和工具调用。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/diagnosis — 提交诊断（支持多轮对话）</li>
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
    private final ProjectManager projectManager;
    private final String modelName;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public DiagnosisController(WorkflowEngine workflowEngine,
                               ModelClient modelClient,
                               PromptRegistry promptRegistry,
                               ToolRegistry toolRegistry,
                               MemoryStore memoryStore,
                               ProjectManager projectManager,
                               @Value("${agentops.llm.model:deepseek-chat}") String modelName,
                               Tracer tracer) {
        this.workflowEngine = workflowEngine;
        this.modelClient = modelClient;
        this.promptRegistry = promptRegistry;
        this.toolRegistry = toolRegistry;
        this.memoryStore = memoryStore;
        this.projectManager = projectManager;
        this.modelName = modelName;
        this.objectMapper = new ObjectMapper();
        this.tracer = tracer;
    }

    /**
     * 提交异常堆栈进行诊断（支持多轮对话）。
     *
     * <pre>
     * POST /api/diagnosis
     * {
     *   "stackTrace": "java.lang.NullPointerException: ...",
     *   "conversationId": "uuid-xxx"   // 可选，用于多轮对话
     * }
     * </pre>
     */
    @PostMapping("/diagnosis")
    public Map<String, Object> diagnose(@RequestBody Map<String, String> body) {
        String rawStackTrace = body.get("stackTrace");
        String conversationId = body.get("conversationId");
        String projectId = body.get("projectId");

        if (rawStackTrace == null || rawStackTrace.isBlank()) {
            return Map.of("success", false, "error", "缺少 stackTrace 字段");
        }

        // 确定使用的工具注册表（项目级 vs 全局）
        ToolRegistry activeToolRegistry = resolveToolRegistry(projectId);

        // 创建诊断 Span，用于分布式追踪
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

            // 2. 渲染诊断 System Prompt（注入项目上下文）
            String systemPrompt = promptRegistry.render("diagnosis-system", Map.of(
                    "exceptionType", parsed.exceptionType(),
                    "exceptionMessage", parsed.message() != null ? parsed.message() : "无",
                    "rawStackTrace", rawStackTrace
            ));
            if (projectId != null) {
                Project project = projectManager.getProject(projectId).orElse(null);
                if (project != null) {
                    systemPrompt = systemPrompt + "\n\n## 项目上下文\n" +
                            "当前检测项目：**" + project.name() + "**\n" +
                            "项目描述：" + (project.description().isBlank() ? "无" : project.description());
                }
            }

            // 3. 构建消息列表（含历史对话）
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPrompt));

            if (conversationId != null && !conversationId.isBlank()) {
                loadConversationHistory(conversationId, messages);
            }

            messages.add(ChatMessage.user("请对以上异常进行诊断分析，只输出 JSON。"));

            // 4. 调用 LLM（带工具）
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

            // 5. 解析 JSON 为 DiagnosisReport（注入 traceId）
            String traceId = span.context().traceId();
            DiagnosisReport report = parseDiagnosisReport(response.content(), parsed.exceptionType(), traceId);

            // 6. 保存对话记录
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
     * 多轮对话端点 — 在已有诊断基础上追问。
     *
     * <pre>
     * POST /api/chat
     * {
     *   "conversationId": "uuid-xxx",
     *   "message": "这个异常最近一次出现是什么时候？"
     * }
     * </pre>
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

            // 1. 加载历史并添加追问 System Prompt
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(
                    "你是一名资深 SRE 和 Java 后端专家。用户正在就之前的异常诊断进行追问，" +
                    "请结合上下文给出专业、具体的回答。如果你需要查看代码仓库或日志，" +
                    "可以使用已注册的工具（git-log/git-blame/git-show/log-search）。"));
            loadConversationHistory(conversationId, messages);
            messages.add(ChatMessage.user(userMessage));

            // 2. 调用 LLM（带工具）
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

            // 3. 保存对话
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

    // ---- 工具注册表解析 ----

    /**
     * 根据 projectId 解析使用的工具注册表。
     * <ul>
     *   <li>有 projectId → 调用 ProjectManager 构建项目专属注册表</li>
     *   <li>无 projectId → 回退到全局 toolRegistry（向后兼容）</li>
     * </ul>
     */
    private ToolRegistry resolveToolRegistry(String projectId) {
        if (projectId != null && !projectId.isBlank()) {
            try {
                return projectManager.buildProjectToolRegistry(projectId);
            } catch (Exception e) {
                // 项目不存在或构建失败时回退到全局注册表
                return toolRegistry;
            }
        }
        return toolRegistry;
    }

    // ---- 对话历史管理 ----

    /**
     * 从 MemoryStore 加载历史对话消息。
     */
    private void loadConversationHistory(String conversationId, List<ChatMessage> target) {
        List<MemoryEntry> history = memoryStore.findByType("conversation:" + conversationId, 20);
        // 按时间正序（最早的在前面）
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
            } catch (JsonProcessingException ignored) {
            }
        }
    }

    /**
     * 保存对话消息到 MemoryStore，返回 conversationId。
     */
    private String saveConversation(String conversationId,
                                     List<ChatMessage> requestMessages,
                                     ChatMessage responseMessage) {
        String cid = conversationId != null && !conversationId.isBlank()
                ? conversationId : UUID.randomUUID().toString();

        try {
            // 保存 System Prompt（仅首次，消息列表第一条）
            if (requestMessages.size() > 1
                    && "system".equals(requestMessages.get(0).role())
                    && requestMessages.get(0).content() != null) {
                String type = "conversation:" + cid;
                // 只在没有历史记录时才保存 system prompt（避免重复）
                if (memoryStore.findByType(type, 1).isEmpty()) {
                    Map<String, String> sysMsg = new HashMap<>();
                    sysMsg.put("role", "system");
                    sysMsg.put("content", requestMessages.get(0).content());
                    memoryStore.save(MemoryEntry.pending(type, objectMapper.writeValueAsString(sysMsg)));
                }
            }

            // 保存最后一条 user 消息和 assistant 回复
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
        } catch (Exception ignored) {
            // 对话保存失败不影响主流程
        }

        return cid;
    }

    /**
     * 将 LLM 返回的 JSON 字符串解析为 DiagnosisReport，注入当前 traceId。
     *
     * @param jsonContent   LLM 返回的 JSON 文本
     * @param exceptionType 异常类型（解析失败时作为 fallback）
     * @param traceId       OpenTelemetry Trace ID（V0.5），可关联分布式追踪
     */
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
            // 用当前 Span 的 traceId 覆盖（LLM 不会生成此字段）
            return new DiagnosisReport(
                    parsed.summary(), parsed.exceptionType(), parsed.severity(),
                    parsed.likelyRootCause(), parsed.impactScope(), parsed.urgency(),
                    parsed.relatedModules(), parsed.recommendations(),
                    parsed.confidence(), traceId);
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
                    traceId
            );
        }
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "0.5.0-SNAPSHOT",
                "prompts", promptRegistry.listNames().size() + " loaded",
                "tools", toolRegistry.listDefinitions().size() + " registered"
        );
    }
}
