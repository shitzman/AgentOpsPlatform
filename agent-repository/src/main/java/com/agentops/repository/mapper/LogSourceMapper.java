package com.agentops.repository.mapper;

import com.agentops.repository.entity.LogSourceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日志源 Mapper — 提供 log_sources 表的 CRUD 操作。
 */
@Mapper
public interface LogSourceMapper extends BaseMapper<LogSourceEntity> {
}
