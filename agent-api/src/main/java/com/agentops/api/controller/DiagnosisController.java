package com.agentops.api.controller;

import com.agentops.business.exceptionagent.BusinessExceptionAgent;
import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.business.exceptionagent.model.StackTrace;
import com.agentops.prompts.PromptRegistry;
import com.agentops.runtime.model.ModelClient;
import com.agentops.runtime.model.ChatMessage;
import com.agentops.runtime.model.ChatRequest;
import com.agentops.runtime.model.ChatResponse;
import com.agentops.workflow.SimpleWorkflowContext;
import com.agentops.workflow.WorkflowContext;
import com.agentops.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 诊断 REST API — 接收异常堆栈，返回 LLM 结构化诊断报告。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/diagnosis — 提交诊断（核心接口），返回结构化 JSON</li>
 *   <li>GET  /api/health     — 健康检查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class DiagnosisController {

    private final WorkflowEngine workflowEngine;
    private final ModelClient modelClient;
    private final PromptRegistry promptRegistry;
    private final String modelName;
    private final ObjectMapper objectMapper;

    public DiagnosisController(WorkflowEngine workflowEngine,
                               ModelClient modelClient,
                               PromptRegistry promptRegistry,
                               @Value("${agentops.llm.model:deepseek-chat}") String modelName) {
        this.workflowEngine = workflowEngine;
        this.modelClient = modelClient;
        this.promptRegistry = promptRegistry;
        this.modelName = modelName;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 提交异常堆栈进行诊断。
     *
     * <p>请求示例：
     * <pre>
     * POST /api/diagnosis
     * Content-Type: application/json
     *
     * {
     *   "stackTrace": "java.lang.NullPointerException: ...\n\tat com.agentops..."
     * }
     * </pre>
     *
     * <p>成功响应包含结构化的 {@link DiagnosisReport} JSON。
     */
    @PostMapping("/diagnosis")
    public Map<String, Object> diagnose(@RequestBody Map<String, String> body) {
        String rawStackTrace = body.get("stackTrace");
        if (rawStackTrace == null || rawStackTrace.isBlank()) {
            return Map.of("success", false, "error", "缺少 stackTrace 字段");
        }

        try {
            // 1. 执行诊断工作流（解析堆栈 + 过滤项目代码）
            WorkflowContext context = new SimpleWorkflowContext();
            context.put(BusinessExceptionAgent.CTX_RAW_STACK_TRACE, rawStackTrace);
            WorkflowContext result = workflowEngine.execute(
                    BusinessExceptionAgent.WORKFLOW_NAME, context);

            StackTrace parsed = result.get(BusinessExceptionAgent.CTX_PARSED_TRACE, StackTrace.class);

            // 2. 渲染诊断 System Prompt
            String systemPrompt = promptRegistry.render("diagnosis-system", Map.of(
                    "exceptionType", parsed.exceptionType(),
                    "exceptionMessage", parsed.message() != null ? parsed.message() : "无",
                    "rawStackTrace", rawStackTrace
            ));

            // 3. 调用 LLM（启用 JSON Mode，强制返回结构化 JSON）
            ChatRequest request = new ChatRequest(
                    List.of(
                            ChatMessage.system(systemPrompt),
                            ChatMessage.user("请对以上异常进行诊断分析，只输出 JSON。")
                    ),
                    List.of(),
                    modelName,
                    0.2,
                    2048,
                    "json_object"   // 启用 JSON Mode
            );

            ChatResponse response = modelClient.chat(request);

            // 4. 解析 LLM 返回的 JSON 为 DiagnosisReport
            DiagnosisReport report = parseDiagnosisReport(response.content(), parsed.exceptionType());

            return Map.of("success", true, "report", report);
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 将 LLM 返回的 JSON 字符串解析为 DiagnosisReport。
     * 解析失败时返回降级报告（保留原始文本）。
     */
    private DiagnosisReport parseDiagnosisReport(String jsonContent, String exceptionType) {
        try {
            // 提取 JSON 部分（LLM 有时会在 JSON 外包裹 markdown 代码块）
            String json = jsonContent.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf("\n");
                int end = json.lastIndexOf("```");
                if (start > 0 && end > start) {
                    json = json.substring(start, end).trim();
                }
            }
            return objectMapper.readValue(json, DiagnosisReport.class);
        } catch (Exception e) {
            // 降级：将原始内容填入 summary 和 likelyRootCause
            return new DiagnosisReport(
                    "JSON 解析失败，以下是原始诊断内容",
                    exceptionType,
                    "medium",
                    jsonContent,
                    "无法确定（解析失败）",
                    "计划修复",
                    List.of(),
                    List.of("请人工查看原始诊断内容"),
                    0.0
            );
        }
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "0.3.0-SNAPSHOT",
                "prompts", promptRegistry.listNames().size() + " loaded"
        );
    }
}
