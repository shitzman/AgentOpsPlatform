package com.agentops.repository.mapper;

import com.agentops.repository.entity.ProjectEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目 Mapper — 提供 projects 表的 CRUD 操作。
 */
@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {
}
