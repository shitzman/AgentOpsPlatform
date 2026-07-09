package com.agentops.api.vo;

/**
 * 工具信息 VO — {@link ToolListResponseVo} 的元素类型。
 *
 * <p>替代旧的 {@code Map<String,String>}（含 name/description 键），JSON 形态一致。
 *
 * @param name        工具名称
 * @param description 工具描述
 */
public record ToolInfoVo(String name, String description) {
}
