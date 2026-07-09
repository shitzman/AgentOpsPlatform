package com.agentops.api.vo;

import com.agentops.repository.entity.ProjectEntity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 项目列表响应 VO — GET /api/projects 的返回结构。
 *
 * @param success  是否成功
 * @param error    错误信息
 * @param projects 项目实体列表
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectListResponseVo(
        boolean success,
        String error,
        List<ProjectEntity> projects) {

    public static ProjectListResponseVo ok(List<ProjectEntity> projects) {
        return new ProjectListResponseVo(true, null, projects);
    }

    public static ProjectListResponseVo error(String message) {
        return new ProjectListResponseVo(false, message, null);
    }
}
