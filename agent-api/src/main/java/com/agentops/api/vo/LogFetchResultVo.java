package com.agentops.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 日志拉取结果 VO。
 *
 * <p>成功：{@code {success:true, content:"日志文本...", lineCount:42}}。
 * 失败：{@code {success:false, message:"日志源不存在..."}}。
 *
 * @param success   是否拉取成功
 * @param content   日志文本内容（成功时返回）
 * @param message   失败原因（失败时返回）
 * @param lineCount 日志行数（成功时返回）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogFetchResultVo(boolean success, String content, String message, Integer lineCount) {

    public static LogFetchResultVo ok(String content, int lineCount) {
        return new LogFetchResultVo(true, content, null, lineCount);
    }

    public static LogFetchResultVo error(String message) {
        return new LogFetchResultVo(false, null, message, null);
    }
}
