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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 诊断 REST API — 接收异常堆栈，返回 LLM 诊断报告。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/diagnosis — 提交诊断（核心接口）</li>
 *   <li>GET  /api/health     — 健康检查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class DiagnosisController {

    private final BusinessExceptionAgent agent;
    private final WorkflowEngine workflowEngine;
    private final ModelClient modelClient;
    private final PromptRegistry promptRegistry;

    public DiagnosisController(BusinessExceptionAgent agent,
                               WorkflowEngine workflowEngine,
                               ModelClient modelClient,
                               PromptRegistry promptRegistry) {
        this.agent = agent;
        this.workflowEngine = workflowEngine;
        this.modelClient = modelClient;
        this.promptRegistry = promptRegistry;
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
     *   "stackTrace": "java.lang.NullPointerException: Cannot invoke...\n\tat com.agentops..."
     * }
     * </pre>
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

            // 3. 调用 LLM 生成诊断报告
            ChatRequest request = new ChatRequest(
                    List.of(
                            ChatMessage.system(systemPrompt),
                            ChatMessage.user("请对以上异常进行诊断分析。")
                    ),
                    List.of(), // V0.2 暂不传工具
                    null,      // 使用默认模型
                    0.2,
                    2048
            );

            ChatResponse response = modelClient.chat(request);

            // 4. 将 LLM 回复包装为诊断报告
            DiagnosisReport report = new DiagnosisReport(
                    response.content(),
                    parsed.exceptionType(),
                    response.content(),
                    List.of(),
                    List.of(),
                    0.7
            );

            return Map.of(
                    "success", true,
                    "report", report,
                    "llmResponse", response.content()
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "0.2.0-SNAPSHOT",
                "prompts", promptRegistry.listNames().size() + " loaded"
        );
    }
}
