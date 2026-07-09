package com.agentops.api.vo;

import com.agentops.business.exceptionagent.model.DiagnosisContext;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 项目上下文快照响应 VO — POST /api/projects/{id}/context 的返回结构。
 *
 * @param success 是否成功
 * @param error   错误信息
 * @param context 聚合了环境/Git/日志的诊断上下文
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectContextResponseVo(
        boolean success,
        String error,
        DiagnosisContext context) {

    public static ProjectContextResponseVo ok(DiagnosisContext context) {
        return new ProjectContextResponseVo(true, null, context);
    }

    public static ProjectContextResponseVo error(String message) {
        return new ProjectContextResponseVo(false, message, null);
    }
}
