package com.agentops.repository.mapper;

import com.agentops.repository.entity.ConversationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话 Mapper — 提供 conversations 表的 CRUD 操作。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {
}
