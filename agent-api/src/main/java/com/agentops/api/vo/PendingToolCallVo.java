package com.agentops.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 待批准的工具调用 VO — LLM 请求调用的工具，展示给用户批准/修改。
 *
 * @param id        工具调用 ID（模型生成，用于关联执行结果）
 * @param name      工具名称
 * @param arguments 工具参数 JSON 字符串（用户可编辑）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PendingToolCallVo(String id, String name, String arguments) {
}
