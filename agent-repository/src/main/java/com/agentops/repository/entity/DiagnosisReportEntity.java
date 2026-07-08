package com.agentops.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 诊断报告实体 — 映射 diagnosis_reports 表。
 *
 * <p>保存每次诊断的结构化报告，支持按项目、异常类型、时间范围查询历史诊断记录。
 */
@TableName("diagnosis_reports")
public class DiagnosisReportEntity {

    @TableId
    private String id;
    private String projectId;
    private String exceptionType;
    private String summary;
    private String rootCause;
    /** JSON 数组字符串 */
    private String relatedModules;
    /** JSON 数组字符串 */
    private String recommendations;
    private Double confidence;
    private String severity;
    private String impactScope;
    private String urgency;
    private String traceId;
    private String rawTrace;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public DiagnosisReportEntity() {}

    // ---- getters ----

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getExceptionType() { return exceptionType; }
    public String getSummary() { return summary; }
    public String getRootCause() { return rootCause; }
    public String getRelatedModules() { return relatedModules; }
    public String getRecommendations() { return recommendations; }
    public Double getConfidence() { return confidence; }
    public String getSeverity() { return severity; }
    public String getImpactScope() { return impactScope; }
    public String getUrgency() { return urgency; }
    public String getTraceId() { return traceId; }
    public String getRawTrace() { return rawTrace; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ---- setters ----

    public void setId(String id) { this.id = id; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public void setRelatedModules(String relatedModules) { this.relatedModules = relatedModules; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public void setSeverity(String severity) { this.severity = severity; }
    public void setImpactScope(String impactScope) { this.impactScope = impactScope; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public void setRawTrace(String rawTrace) { this.rawTrace = rawTrace; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
