package com.agentops.tools.core;

/**
 * 工具执行结果 — {@link ToolExecutor} 执行后返回的结果封装。
 *
 * <p>结果分为成功和失败两种情况：
 * <ul>
 *   <li>成功：{@code success=true}，{@code output} 包含工具的输出内容</li>
 *   <li>失败：{@code success=false}，{@code error} 包含错误描述</li>
 * </ul>
 *
 * @param success 是否执行成功
 * @param output  成功时的输出内容（失败时为 null）
 * @param error   失败时的错误信息（成功时为 null）
 */
public record ToolResult(
        boolean success,
        String output,
        String error) {

    /**
     * 创建成功结果。
     *
     * @param output 工具输出内容
     * @return 成功的 ToolResult
     */
    public static ToolResult success(String output) {
        return new ToolResult(true, output, null);
    }

    /**
     * 创建失败结果。
     *
     * @param error 错误描述
     * @return 失败的 ToolResult
     */
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error);
    }
}
