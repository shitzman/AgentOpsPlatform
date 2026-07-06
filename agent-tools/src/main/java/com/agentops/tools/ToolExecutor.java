package com.agentops.tools;

import java.util.Map;

/**
 * Executes a tool with the given arguments.
 *
 * <p>Implementations are registered with the {@link ToolRegistry} alongside
 * their {@link ToolDefinition}. The registry resolves an executor by tool name
 * and delegates execution to it.
 *
 * <p>This is a functional interface so implementations can use lambdas or
 * method references where appropriate.
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * Executes the tool with the given arguments.
     *
     * @param arguments the tool arguments, keyed by parameter name
     * @return the result of the execution, never null
     */
    ToolResult execute(Map<String, Object> arguments);
}
