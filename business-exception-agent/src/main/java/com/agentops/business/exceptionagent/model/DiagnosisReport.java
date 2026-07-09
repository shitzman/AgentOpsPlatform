package com.agentops.business.exceptionagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * 诊断报告 — Business Exception Agent 的结构化输出。
 *
 * <p>此模型同时用于两个目的：
 * <ol>
 *   <li>作为 LLM JSON Mode 输出的目标 Schema（通过 Prompt 中的 JSON Schema 描述）</li>
 *   <li>作为 REST API 响应的序列化对象（Jackson 自动序列化）</li>
 * </ol>
 *
 * <p>V1.0 Phase 3 新增多源关联字段：gitBlameHints / environmentFactors / logContextSummary
 *
 * @param summary             异常摘要（一句话描述发生了什么）
 * @param exceptionType       异常类型全名
 * @param severity            严重级别：critical / high / medium / low
 * @param likelyRootCause     最可能的根因分析
 * @param impactScope         影响范围（涉及哪些模块、服务或用户）
 * @param urgency             紧急程度：立即修复 / 计划修复 / 低优先级
 * @param relatedModules      可能相关的模块列表
 * @param recommendations     修复建议列表（按优先级排列）
 * @param confidence          诊断置信度 (0.0–1.0)
 * @param traceId             OpenTelemetry Trace ID（V0.5），用于关联分布式追踪数据
 * @param gitBlameHints       可疑的 Git 提交线索列表（V1.0 Phase 3）
 * @param environmentFactors  可能相关的环境因素列表（V1.0 Phase 3）
 * @param logContextSummary   关联日志上下文的关键发现摘要（V1.0 Phase 3）
 * @param followUpQuestions   信息不足时的引导性追问列表（V1.4），引导用户补充信息
 */
public record DiagnosisReport(
        String summary,
        String exceptionType,
        String severity,
        String likelyRootCause,
        String impactScope,
        String urgency,
        List<String> relatedModules,
        List<String> recommendations,
        double confidence,
        String traceId,
        List<String> gitBlameHints,
        List<String> environmentFactors,
        String logContextSummary,
        List<String> followUpQuestions) {

    public DiagnosisReport {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("诊断摘要不能为空");
        }
        if (exceptionType == null || exceptionType.isBlank()) {
            throw new IllegalArgumentException("异常类型不能为空");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("置信度必须在 0.0–1.0 之间");
        }
        relatedModules = relatedModules != null
                ? Collections.unmodifiableList(relatedModules)
                : Collections.emptyList();
        recommendations = recommendations != null
                ? Collections.unmodifiableList(recommendations)
                : Collections.emptyList();
        gitBlameHints = gitBlameHints != null
                ? Collections.unmodifiableList(gitBlameHints)
                : Collections.emptyList();
        environmentFactors = environmentFactors != null
                ? Collections.unmodifiableList(environmentFactors)
                : Collections.emptyList();
        followUpQuestions = followUpQuestions != null
                ? Collections.unmodifiableList(followUpQuestions)
                : Collections.emptyList();
        // traceId 和 logContextSummary 为可选字段，允许 null
    }
}
