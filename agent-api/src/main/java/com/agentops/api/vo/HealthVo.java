package com.agentops.api.vo;

/**
 * 健康检查响应 VO — GET /api/health 的返回结构。
 *
 * <p>固定结构，无 success/error 字段：{@code {status, version, prompts, tools}}。
 * 其中 {@code prompts}/{@code tools} 为描述性字符串（如 "4 loaded"、"4 registered"）。
 *
 * @param status  状态（"UP"）
 * @param version 应用版本
 * @param prompts 已加载的 Prompt 模板描述
 * @param tools   已注册的工具描述
 */
public record HealthVo(
        String status,
        String version,
        String prompts,
        String tools) {
}
