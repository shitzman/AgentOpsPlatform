package com.agentops.api.service;

import com.agentops.business.exceptionagent.model.DiagnosisReport;
import com.agentops.repository.entity.DiagnosisReportEntity;
import com.agentops.repository.mapper.DiagnosisReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 诊断报告持久化服务 — 保存诊断结果到 MySQL 并支持历史分页查询。
 *
 * <p>从 {@code DiagnosisController} 抽取。保存失败被静默忽略，不影响主诊断流程。
 */
@Service
public class DiagnosisReportPersistenceService {

    private final DiagnosisReportMapper diagnosisReportMapper;
    private final ObjectMapper objectMapper;

    public DiagnosisReportPersistenceService(DiagnosisReportMapper diagnosisReportMapper) {
        this.diagnosisReportMapper = diagnosisReportMapper;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 保存一条诊断报告。
     *
     * @param projectId 项目 ID（可为 null）
     * @param report    诊断报告
     * @param rawTrace  原始堆栈/日志文本
     */
    public void saveReport(String projectId, DiagnosisReport report, String rawTrace) {
        try {
            DiagnosisReportEntity entity = toEntity(projectId, report, rawTrace);
            diagnosisReportMapper.insert(entity);
        } catch (Exception ignored) {
            // 持久化失败不影响主诊断流程
        }
    }

    /**
     * 分页查询诊断历史。
     *
     * @param projectId 项目 ID（为空时查全部）
     * @param page      页码（从 0 开始）
     * @param size      每页条数
     * @return 分页结果
     */
    public ListResult listReports(String projectId, int page, int size) {
        QueryWrapper<DiagnosisReportEntity> wrapper = buildQueryWrapper(projectId);
        // 先查总数（H2 要求聚合查询不能带 ORDER BY / LIMIT）
        long total = diagnosisReportMapper.selectCount(wrapper);
        // 再追加排序与分页
        wrapper.orderByDesc("created_at");
        wrapper.last("LIMIT " + (page * size) + "," + size);
        List<DiagnosisReportEntity> reports = diagnosisReportMapper.selectList(wrapper);
        return new ListResult(reports, total, page, size);
    }

    // ---- 私有辅助 ----

    private QueryWrapper<DiagnosisReportEntity> buildQueryWrapper(String projectId) {
        QueryWrapper<DiagnosisReportEntity> wrapper = new QueryWrapper<>();
        if (projectId != null && !projectId.isBlank()) {
            wrapper.eq("project_id", projectId);
        }
        return wrapper;
    }

    private DiagnosisReportEntity toEntity(String projectId, DiagnosisReport report, String rawTrace)
            throws JsonProcessingException {
        DiagnosisReportEntity entity = new DiagnosisReportEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setProjectId(projectId);
        entity.setExceptionType(report.exceptionType());
        entity.setSummary(report.summary());
        entity.setRootCause(report.likelyRootCause());
        entity.setRelatedModules(objectMapper.writeValueAsString(report.relatedModules()));
        entity.setRecommendations(objectMapper.writeValueAsString(report.recommendations()));
        entity.setConfidence(report.confidence());
        entity.setSeverity(report.severity());
        entity.setImpactScope(report.impactScope());
        entity.setUrgency(report.urgency());
        entity.setTraceId(report.traceId());
        entity.setRawTrace(rawTrace);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    /** 分页查询结果载体（不序列化） */
    public record ListResult(List<DiagnosisReportEntity> reports, long total, int page, int size) {
    }
}
