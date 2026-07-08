package com.agentops.repository.mapper;

import com.agentops.repository.entity.MemoryEntryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Memory Entry Mapper — 提供 memory_entries 表的 CRUD 操作。
 *
 * <p>MySqlMemoryStore 通过此 Mapper 实现 MemoryStore 接口。
 */
@Mapper
public interface MemoryEntryMapper extends BaseMapper<MemoryEntryEntity> {
}
