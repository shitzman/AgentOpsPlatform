package com.agentops.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 通用结果响应 VO — 用于删除等仅需返回成功/失败的端点。
 *
 * <p>成功：{@code {success:true}}。失败：{@code {success:false, error:"..."}}。
 *
 * @param success 是否成功
 * @param error   错误信息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimpleResultVo(boolean success, String error) {

    public static SimpleResultVo ok() {
        return new SimpleResultVo(true, null);
    }

    public static SimpleResultVo error(String message) {
        return new SimpleResultVo(false, message);
    }
}
