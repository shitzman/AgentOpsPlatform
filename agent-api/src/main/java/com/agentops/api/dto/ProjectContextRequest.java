package com.agentops.api.dto;

/**
 * 项目上下文快照请求 DTO — 聚合环境、Git、日志数据。
 *
 * @param logContent 原始日志文本（可选，提供后自动提取堆栈与上下文）
 */
public record ProjectContextRequest(String logContent) {
}
