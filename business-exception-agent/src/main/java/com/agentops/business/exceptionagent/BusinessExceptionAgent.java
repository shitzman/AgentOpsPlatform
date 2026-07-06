package com.agentops.business.exceptionagent;

import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.business.exceptionagent.model.StackTrace;
import com.agentops.business.exceptionagent.model.StackTraceFrame;
import com.agentops.workflow.WorkflowContext;
import com.agentops.workflow.WorkflowDefinition;
import com.agentops.workflow.WorkflowEngine;
import com.agentops.workflow.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Business Exception Agent — 平台首个领域 Agent。
 *
 * <p>核心职责：接收异常堆栈，通过诊断工作流产出结构化诊断报告。
 *
 * <p>诊断工作流分为以下步骤：
 * <ol>
 *   <li><b>解析堆栈</b> — 从原始文本中提取异常类型、消息和堆栈帧</li>
 *   <li><b>过滤项目代码</b> — 标记属于项目自身的堆栈帧（过滤框架/第三方库）</li>
 *   <li><b>生成诊断报告</b> — 基于分析结果生成报告（V0.2 接入 LLM）</li>
 * </ol>
 *
 * <p>V0.1 产出诊断工作流骨架。V0.2 将接入 ModelClient 做 LLM 驱动的根因分析。
 *
 * <p>使用示例：
 * <pre>{@code
 *   BusinessExceptionAgent agent = new BusinessExceptionAgent(engine);
 *   String rawTrace = "java.lang.NullPointerException\n\tat com.example.OrderService...";
 *   DiagnosisReport report = agent.diagnose(rawTrace);
 * }</pre>
 */
public class BusinessExceptionAgent {

    /** 诊断工作流名称 */
    public static final String WORKFLOW_NAME = "business-exception-diagnosis";

    /** 上下文键名：原始堆栈文本 */
    public static final String CTX_RAW_STACK_TRACE = "rawStackTrace";
    /** 上下文键名：解析后的 StackTrace 对象 */
    public static final String CTX_PARSED_TRACE = "parsedTrace";
    /** 上下文键名：最终诊断报告 */
    public static final String CTX_DIAGNOSIS_REPORT = "diagnosisReport";

    /** 堆栈帧正则：at <类名>.<方法名>(<文件>:<行号>) */
    private static final Pattern FRAME_PATTERN =
            Pattern.compile("at\\s+([\\w.$]+)\\.([\\w<>$]+)\\(([^)]*)\\)");

    private final WorkflowEngine engine;
    private final WorkflowDefinition workflowDefinition;

    public BusinessExceptionAgent(WorkflowEngine engine) {
        this.engine = engine;
        this.workflowDefinition = buildWorkflow();
        engine.register(workflowDefinition);
    }

    /**
     * 执行诊断，输入原始堆栈文本，输出结构化诊断报告。
     *
     * @param rawStackTrace 原始异常堆栈文本
     * @return 诊断报告
     */
    public DiagnosisReport diagnose(String rawStackTrace) {
        WorkflowContext input = engine.execute(WORKFLOW_NAME, null);
        // 简易上下文：直接构建并注入初始数据
        // 实际实现由 WorkflowEngine 的 execute(definition, context) 负责
        // 这里演示 Workflow 通过注册名执行的方式

        // 注：当前 execute(name, context) 需要 initialContext，
        // 由 WorkflowEngine 实现负责创建并注入 rawStackTrace
        // V0.2 完善此流程
        return new DiagnosisReport(
                "诊断工作流骨架已执行（V0.2 接入 LLM）",
                "unknown", "medium", "待 LLM 分析",
                "未知", "计划修复", List.of(), List.of(), 0.0);
    }

    /**
     * 构建诊断工作流定义。
     */
    private WorkflowDefinition buildWorkflow() {
        List<WorkflowStep> steps = List.of(
                parseStackTraceStep(),
                filterProjectCodeStep(),
                generateReportStep()
        );
        return new WorkflowDefinition(WORKFLOW_NAME, steps);
    }

    // ---- 工作流步骤 ----

    /**
     * 步骤 1：解析原始堆栈文本，提取异常类型、消息和堆栈帧。
     */
    WorkflowStep parseStackTraceStep() {
        return context -> {
            String raw = context.get(CTX_RAW_STACK_TRACE, String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("缺少原始堆栈文本 (" + CTX_RAW_STACK_TRACE + ")");
            }

            String[] lines = raw.split("\\r?\\n");
            String exceptionType = "unknown";
            String message = null;
            List<StackTraceFrame> frames = new ArrayList<>();

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // 第一行通常是异常类型和消息
                if (exceptionType.equals("unknown") && !line.startsWith("at ")) {
                    int colonIdx = line.indexOf(':');
                    if (colonIdx > 0) {
                        exceptionType = line.substring(0, colonIdx).trim();
                        message = line.substring(colonIdx + 1).trim();
                    } else {
                        exceptionType = line.trim();
                    }
                    continue;
                }

                // 后续行为堆栈帧
                Matcher m = FRAME_PATTERN.matcher(line);
                if (m.find()) {
                    String className = m.group(1);
                    String methodName = m.group(2);
                    String fileInfo = m.group(3);
                    String fileName = null;
                    int lineNumber = -1;

                    int colonIdx = fileInfo.lastIndexOf(':');
                    if (colonIdx > 0) {
                        fileName = fileInfo.substring(0, colonIdx);
                        try {
                            lineNumber = Integer.parseInt(fileInfo.substring(colonIdx + 1));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (!fileInfo.equals("Unknown Source") && !fileInfo.equals("Native Method")) {
                        fileName = fileInfo;
                    }

                    frames.add(new StackTraceFrame(className, methodName, fileName, lineNumber, false));
                }
            }

            StackTrace parsed = new StackTrace(exceptionType, message, frames, raw);
            context.put(CTX_PARSED_TRACE, parsed);
            return context;
        };
    }

    /**
     * 步骤 2：标记属于项目自身代码的堆栈帧。
     *
     * <p>项目代码判定规则：包名以 {@code com.agentops} 或用户指定的包前缀开头。
     * V0.2 将支持通过配置或 Agent 上下文注入项目包前缀列表。
     */
    WorkflowStep filterProjectCodeStep() {
        return context -> {
            StackTrace trace = context.get(CTX_PARSED_TRACE, StackTrace.class);
            if (trace == null) {
                throw new IllegalArgumentException("缺少解析后的堆栈 (" + CTX_PARSED_TRACE + ")");
            }

            // V0.1：默认将 com.agentops 下的代码标记为项目代码
            List<StackTraceFrame> marked = trace.frames().stream()
                    .map(frame -> new StackTraceFrame(
                            frame.className(),
                            frame.methodName(),
                            frame.fileName(),
                            frame.lineNumber(),
                            frame.className().startsWith("com.agentops")))
                    .toList();

            context.put(CTX_PARSED_TRACE,
                    new StackTrace(trace.exceptionType(), trace.message(), marked, trace.rawText()));
            return context;
        };
    }

    /**
     * 步骤 3：生成诊断报告。
     *
     * <p>V0.1 产出占位报告。V0.2 接入 ModelClient，通过 LLM 分析堆栈
     * 并生成完整的 DiagnosisReport（包含根因分析、关联模块和修复建议）。
     */
    WorkflowStep generateReportStep() {
        return context -> {
            StackTrace trace = context.get(CTX_PARSED_TRACE, StackTrace.class);
            if (trace == null) {
                throw new IllegalArgumentException("缺少解析后的堆栈 (" + CTX_PARSED_TRACE + ")");
            }

            // 提取项目相关的堆栈帧作为关键线索
            List<StackTraceFrame> projectFrames = trace.frames().stream()
                    .filter(StackTraceFrame::isProjectCode)
                    .toList();

            String summary = String.format(
                    "%s: %s（涉及 %d 个项目代码帧）",
                    trace.exceptionType(),
                    trace.message() != null ? trace.message() : "无消息",
                    projectFrames.size());

            DiagnosisReport report = new DiagnosisReport(
                    summary, trace.exceptionType(), "medium",
                    "待 LLM 分析", "未知", "计划修复",
                    List.of(), List.of(), 0.0);
            context.put(CTX_DIAGNOSIS_REPORT, report);
            return context;
        };
    }
}
