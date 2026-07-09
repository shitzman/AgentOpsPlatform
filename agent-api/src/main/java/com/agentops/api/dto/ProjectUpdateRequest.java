package com.agentops.api.dto;

import java.util.Collections;
import java.util.List;

/**
 * 更新项目请求 DTO — 部分更新语义，仅传入的非 null 字段会被更新。
 *
 * <p>Service 层将非 null 字段转换为 Map 后委派给 {@code MySqlProjectManager.updateProject}，
 * 保留其基于 {@code containsKey} 的部分更新行为。
 *
 * @param name            项目名称
 * @param description     项目描述
 * @param gitRepoUrl      Git 远程仓库地址
 * @param gitRepoLocalPath Git 本地仓库路径
 * @param enabledTools    启用的工具名称列表
 * @param logSourceIds    关联的日志源 ID 列表
 */
public record ProjectUpdateRequest(
        String name,
        String description,
        String gitRepoUrl,
        String gitRepoLocalPath,
        List<String> enabledTools,
        List<String> logSourceIds) {

    public ProjectUpdateRequest {
        enabledTools = enabledTools != null
                ? Collections.unmodifiableList(enabledTools)
                : null;
        logSourceIds = logSourceIds != null
                ? Collections.unmodifiableList(logSourceIds)
                : null;
    }
}
