package com.agentops.api.vo;

import com.agentops.tools.LogSourceConfig;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 日志源列表响应 VO — GET /api/projects/{id}/logsources 的返回结构。
 *
 * @param success    是否成功
 * @param error      错误信息
 * @param logSources 日志源配置列表
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogSourceListResponseVo(
        boolean success,
        String error,
        List<LogSourceConfig> logSources) {

    public static LogSourceListResponseVo ok(List<LogSourceConfig> logSources) {
        return new LogSourceListResponseVo(true, null, logSources);
    }

    public static LogSourceListResponseVo error(String message) {
        return new LogSourceListResponseVo(false, message, null);
    }
}
