package com.agentops.repository.mapper;

import com.agentops.repository.entity.DiagnosisReportEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 诊断报告 Mapper — 提供 diagnosis_reports 表的 CRUD 操作。
 */
@Mapper
public interface DiagnosisReportMapper extends BaseMapper<DiagnosisReportEntity> {
}
