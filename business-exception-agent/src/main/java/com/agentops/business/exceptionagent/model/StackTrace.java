package com.agentops.business.exceptionagent.model;

import java.util.Collections;
import java.util.List;

/**
 * 异常堆栈 — 从线上日志或用户提交中提取的结构化异常信息。
 *
 * <p>这是 Business Exception Agent 的核心输入模型。包含原始文本
 * 和解析后的结构化数据，两者同时保留以供不同诊断步骤使用。
 *
 * @param exceptionType 异常类型全名（如 java.lang.NullPointerException）
 * @param message       异常消息（如 "Cannot invoke 'String.length()' because 's' is null"）
 * @param frames        堆栈帧列表（按从近到远排列）
 * @param rawText       原始堆栈文本（保留用于 LLM Prompt 注入）
 */
public record StackTrace(
        String exceptionType,
        String message,
        List<StackTraceFrame> frames,
        String rawText) {

    public StackTrace {
        if (exceptionType == null || exceptionType.isBlank()) {
            throw new IllegalArgumentException("异常类型不能为空");
        }
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("原始堆栈文本不能为空");
        }
        frames = frames != null
                ? Collections.unmodifiableList(frames)
                : Collections.emptyList();
    }

    /**
     * 从原始堆栈文本创建 StackTrace（不做解析，仅保留原始文本）。
     * 解析由诊断工作流的第一步完成。
     */
    public static StackTrace fromRaw(String rawText) {
        return new StackTrace("unknown", null, Collections.emptyList(), rawText);
    }
}
