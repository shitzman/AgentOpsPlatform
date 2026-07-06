/**
 * Memory 层 — Agent 执行上下文的持久化抽象。
 *
 * <h3>核心接口</h3>
 * <ul>
 *   <li>{@link com.agentops.memory.MemoryEntry} — 不可变记忆条目（id/type/content/metadata/timestamp）</li>
 *   <li>{@link com.agentops.memory.MemoryStore} — 存储层 CRUD + 搜索契约</li>
 * </ul>
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@link com.agentops.memory.InMemoryMemoryStore} — 内存实现（开发/测试用）</li>
 * </ul>
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>对话记忆 — 保存 Agent 与用户的每轮对话记录</li>
 *   <li>诊断记录 — 保存每次异常诊断的结果</li>
 *   <li>工具调用日志 — 保存工具调用的输入输出</li>
 * </ul>
 */
package com.agentops.memory;
