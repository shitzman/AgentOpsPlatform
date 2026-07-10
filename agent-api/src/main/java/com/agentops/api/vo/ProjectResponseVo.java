package com.agentops.api.vo;

import com.agentops.repository.entity.ProjectEntity;
import com.agentops.tools.log.LogSourceConfig;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 项目响应 VO — 用于项目的创建/获取/更新/工具设置端点。
 *
 * <p>成功（创建/更新/工具设置）：{@code {success:true, project:{...}}}。
 * 成功（获取详情）：{@code {success:true, project:{...}, logSources:[...]}}。
 * 失败：{@code {success:false, error:"..."}}。
 *
 * @param success    是否成功
 * @param error      错误信息
 * @param project    项目实体
 * @param logSources 关联日志源列表（仅获取详情时存在）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectResponseVo(
        boolean success,
        String error,
        ProjectEntity project,
        List<LogSourceConfig> logSources) {

    public static ProjectResponseVo ok(ProjectEntity project) {
        return new ProjectResponseVo(true, null, project, null);
    }

    public static ProjectResponseVo ok(ProjectEntity project, List<LogSourceConfig> logSources) {
        return new ProjectResponseVo(true, null, project, logSources);
    }

    public static ProjectResponseVo error(String message) {
        return new ProjectResponseVo(false, message, null, null);
    }
}
