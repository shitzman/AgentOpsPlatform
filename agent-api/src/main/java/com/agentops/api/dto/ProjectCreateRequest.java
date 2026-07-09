package com.agentops.api.dto;

/**
 * 创建项目请求 DTO。
 *
 * @param name            项目名称（必填，Service 层校验）
 * @param description     项目描述（可选）
 * @param gitRepoUrl      Git 远程仓库地址（可选）
 * @param gitRepoLocalPath Git 本地仓库路径（可选，缺省时 Service 层回退到 user.dir）
 */
public record ProjectCreateRequest(
        String name,
        String description,
        String gitRepoUrl,
        String gitRepoLocalPath) {
}
