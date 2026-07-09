package com.agentops.api.vo;

import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 诊断响应 VO — POST /api/diagnosis 的返回结构。
 *
 * <p>成功：{@code {success:true, report:{...}, conversationId:"..."}}。
 * 失败：{@code {success:false, error:"..."}}（report/conversationId 为 null 被 {@code @JsonInclude} 省略）。
 *
 * @param success       是否成功（primitive，始终序列化）
 * @param error         错误信息（仅失败时存在）
 * @param report        诊断报告（仅成功时存在）
 * @param conversationId 会话 ID（仅成功时存在，用于后续追问）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagnosisResponseVo(
        boolean success,
        String error,
        DiagnosisReport report,
        String conversationId) {

    /** 快速构建成功响应 */
    public static DiagnosisResponseVo ok(DiagnosisReport report, String conversationId) {
        return new DiagnosisResponseVo(true, null, report, conversationId);
    }

    /** 快速构建失败响应 */
    public static DiagnosisResponseVo error(String message) {
        return new DiagnosisResponseVo(false, message, null, null);
    }
}
