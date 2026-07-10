package com.agentops.business.exceptionagent;

import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.runtime.model.ChatMessage;

import java.util.List;

/**
 * 诊断结果 — {@link DiagnosisOrchestrator} 返回给 delivery 层的完整产出。
 *
 * @param report     结构化诊断报告
 * @param rawContent LLM 原始文本输出（用于对话历史保存）
 * @param messages   完整消息列表（含 system + history + tool calls + tool results + assistant）
 */
public record DiagnosisOutcome(DiagnosisReport report, String rawContent, List<ChatMessage> messages) {
}
