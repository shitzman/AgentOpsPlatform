package com.agentops.tools;

/**
 * The result of a tool execution via {@link ToolExecutor}.
 *
 * @param success whether the execution completed successfully
 * @param output  the tool's output content when successful
 * @param error   the error message when execution failed
 */
public record ToolResult(
        boolean success,
        String output,
        String error) {

    /**
     * Creates a successful result with the given output.
     */
    public static ToolResult success(String output) {
        return new ToolResult(true, output, null);
    }

    /**
     * Creates a failed result with the given error message.
     */
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error);
    }
}
