package com.agentops.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 工具列表响应 VO — GET /api/tools 的返回结构。
 *
 * @param success 是否成功
 * @param error   错误信息
 * @param tools   可用工具信息列表
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolListResponseVo(
        boolean success,
        String error,
        List<ToolInfoVo> tools) {

    public static ToolListResponseVo ok(List<ToolInfoVo> tools) {
        return new ToolListResponseVo(true, null, tools);
    }

    public static ToolListResponseVo error(String message) {
        return new ToolListResponseVo(false, message, null);
    }
}
