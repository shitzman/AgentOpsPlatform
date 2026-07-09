package com.agentops.api.dto;

import java.util.Collections;
import java.util.List;

/**
 * 设置项目启用工具请求 DTO。
 *
 * @param toolNames 工具名称列表（必填，Service 层校验）
 */
public record ProjectToolsUpdateRequest(List<String> toolNames) {

    public ProjectToolsUpdateRequest {
        toolNames = toolNames != null
                ? Collections.unmodifiableList(toolNames)
                : Collections.emptyList();
    }
}
