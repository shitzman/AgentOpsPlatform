package com.agentops.api.vo;

import com.agentops.repository.entity.DiagnosisReportEntity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 诊断历史响应 VO — GET /api/diagnosis 的返回结构。
 *
 * <p>成功：{@code {success:true, reports:[...], total, page, size}}。
 * 失败：{@code {success:false, error:"..."}}。
 *
 * <p>注意：{@code total/page/size} 必须为 boxed 类型，否则错误响应会序列化成 0，
 * 破坏前端契约。
 *
 * @param success 是否成功
 * @param error   错误信息
 * @param reports 诊断报告实体列表
 * @param total   总条数
 * @param page    当前页码
 * @param size    每页条数
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagnosisHistoryResponseVo(
        boolean success,
        String error,
        List<DiagnosisReportEntity> reports,
        Long total,
        Integer page,
        Integer size) {

    public static DiagnosisHistoryResponseVo ok(List<DiagnosisReportEntity> reports,
                                                long total, int page, int size) {
        return new DiagnosisHistoryResponseVo(true, null, reports, total, page, size);
    }

    public static DiagnosisHistoryResponseVo error(String message) {
        return new DiagnosisHistoryResponseVo(false, message, null, null, null, null);
    }
}
