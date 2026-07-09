package com.agentops.api.dto;

/**
 * 日志拉取请求 DTO — 按日志源 ID 拉取日志内容用于异常分析。
 *
 * @param keyword 关键词过滤（可选，为空时拉取全部受 limit 限制的日志行）
 * @param limit   最大返回行数（可选，为空时默认 200）
 */
public record LogFetchRequest(String keyword, Integer limit) {
}
