package com.agentops.tools;

import java.util.List;
import java.util.Optional;

/**
 * Central registry for tools available to domain agents.
 *
 * <p>All external capabilities must be represented as tools and accessed
 * through this registry. Domain agents must not bypass the registry to
 * access databases, Git repositories, logs, metrics, or external systems.
 *
 * <p>Expected usage in the reasoning loop:
 * <pre>{@code
 *   // Look up tool definitions to present to the LLM
 *   List<ToolDefinition> available = registry.listDefinitions();
 *
 *   // After the LLM selects a tool, execute it
 *   ToolExecutor executor = registry.getExecutor("log-search")
 *       .orElseThrow(() -> new ToolNotFoundException("Unknown tool: log-search"));
 *   ToolResult result = executor.execute(arguments);
 * }</pre>
 */
public interface ToolRegistry {

    /**
     * Registers a tool definition and its executor.
     *
     * @param definition the tool's metadata
     * @param executor   the tool's execution logic
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    void register(ToolDefinition definition, ToolExecutor executor);

    /**
     * Removes a tool from the registry.
     *
     * @param name the tool name to unregister
     */
    void unregister(String name);

    /**
     * Returns the tool definition for the given name.
     *
     * @param name the tool name
     * @return the definition, or empty if not found
     */
    Optional<ToolDefinition> getDefinition(String name);

    /**
     * Returns the tool executor for the given name.
     *
     * @param name the tool name
     * @return the executor, or empty if not found
     */
    Optional<ToolExecutor> getExecutor(String name);

    /**
     * Returns all registered tool definitions.
     *
     * @return an unmodifiable list of definitions
     */
    List<ToolDefinition> listDefinitions();
}
