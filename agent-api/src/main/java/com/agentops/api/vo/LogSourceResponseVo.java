package com.agentops.api.vo;

import com.agentops.tools.LogSourceConfig;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 日志源响应 VO — 用于日志源的添加/更新端点。
 *
 * @param success   是否成功
 * @param error     错误信息
 * @param logSource 日志源配置
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogSourceResponseVo(
        boolean success,
        String error,
        LogSourceConfig logSource) {

    public static LogSourceResponseVo ok(LogSourceConfig logSource) {
        return new LogSourceResponseVo(true, null, logSource);
    }

    public static LogSourceResponseVo error(String message) {
        return new LogSourceResponseVo(false, message, null);
    }
}
