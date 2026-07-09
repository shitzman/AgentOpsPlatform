package com.agentops.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 日志源测试连接结果 VO。
 *
 * <p>成功：{@code {success:true, message:"连接成功 (ES 版本: 8.x.x)"}}。
 * 失败：{@code {success:false, message:"无法连接 ES 服务..."}}。
 *
 * @param success 是否测试通过
 * @param message 测试结果描述（成功时含版本信息，失败时含原因）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogSourceTestResultVo(boolean success, String message) {

    public static LogSourceTestResultVo ok(String message) {
        return new LogSourceTestResultVo(true, message);
    }

    public static LogSourceTestResultVo error(String message) {
        return new LogSourceTestResultVo(false, message);
    }
}
