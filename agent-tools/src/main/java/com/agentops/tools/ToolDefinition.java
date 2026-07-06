package com.agentops.tools;

import java.util.Map;

/**
 * Describes a tool that can be registered in the {@link ToolRegistry}.
 *
 * <p>The {@code parameters} map represents a JSON Schema object that defines
 * the tool's input contract. This format is compatible with OpenAI function
 * calling and other LLM tool-use APIs.
 *
 * @param name        unique tool name, stable across registrations
 * @param description human-readable description presented to the LLM
 * @param parameters  JSON Schema describing the tool's input parameters
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters) {

    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
    }
}
