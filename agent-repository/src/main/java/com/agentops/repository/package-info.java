/**
 * AgentOps Repository — MySQL + MyBatis-Plus 数据持久化层。
 *
 * <p>本模块负责：
 * <ul>
 *   <li>Entity 定义（MyBatis-Plus 实体，映射 MySQL 表）</li>
 *   <li>Mapper 接口（继承 BaseMapper，开箱即用 CRUD）</li>
 *   <li>MemoryStore 的 MySQL 实现（MySqlMemoryStore）</li>
 *   <li>项目管理服务（MySqlProjectManager，替代旧版 JSON 序列化方案）</li>
 * </ul>
 *
 * <p>模块边界：
 * <ul>
 *   <li>依赖 agent-memory（MemoryStore 接口）和 agent-tools（ToolRegistry 等）</li>
 *   <li>被 agent-api 依赖（作为持久化能力提供方）</li>
 *   <li>不依赖 business-exception-agent（避免循环依赖）</li>
 * </ul>
 */
package com.agentops.repository;
