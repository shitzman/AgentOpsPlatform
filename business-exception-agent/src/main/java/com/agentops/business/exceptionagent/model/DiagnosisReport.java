package com.agentops.business.exceptionagent.model;

import java.util.Collections;
import java.util.List;

/**
 * 诊断报告 — Business Exception Agent 的结构化输出。
 *
 * <p>包含异常摘要、可能的根因分析、关联模块和修复建议。
 * 设计为 LLM 结构化输出的目标 Schema。
 *
 * @param summary          异常摘要（一句话描述发生了什么）
 * @param exceptionType    异常类型
 * @param likelyRootCause  最可能的根因分析
 * @param relatedModules   可能相关的模块/服务列表
 * @param recommendations  修复建议列表（按优先级排列）
 * @param confidence       诊断置信度 (0.0–1.0)
 */
public record DiagnosisReport(
        String summary,
        String exceptionType,
        String likelyRootCause,
        List<String> relatedModules,
        List<String> recommendations,
        double confidence) {

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
    }

    /**
     * 创建一个简单的诊断报告（用于占位和测试）。
     */
    public static DiagnosisReport placeholder(String exceptionType, String summary) {
        return new DiagnosisReport(
                summary,
                exceptionType,
                "需要 LLM 分析确定根因（V0.2 实现）",
                Collections.emptyList(),
                List.of("检查相关服务日志", "查看最近的代码变更", "确认依赖服务是否正常"),
                0.0);
    }
}
