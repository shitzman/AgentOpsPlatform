package com.agentops.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 已执行工具的结果 VO — 展示给用户查看工具调用的中间产物。
 *
 * <p>每次用户批准工具调用后，{@code POST /api/chat/continue} 执行工具并返回此 VO，
 * 让用户看到工具实际执行了什么、返回了什么结果，再由 LLM 基于结果继续推理。
 *
 * @param id        工具调用 ID（模型生成，关联待批准列表中的 id）
 * @param name      工具名称
 * @param arguments 执行时使用的参数 JSON 字符串（可能被用户修改）
 * @param result    执行结果文本（成功取 output，失败取 error 描述）
 * @param success   是否执行成功
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolExecutionResultVo(
        String id,
        String name,
        String arguments,
        String result,
        boolean success) {
}
