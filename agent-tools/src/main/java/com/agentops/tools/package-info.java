/**
 * 工具层 — 所有外部能力都以 Tool 的形式封装。
 *
 * <h3>核心约束</h3>
 * 领域 Agent 禁止绕过 ToolRegistry 直接访问数据库、Git、日志、指标或任何外部系统。
 *
 * <h3>核心接口</h3>
 * <ul>
 *   <li>{@link com.agentops.tools.ToolDefinition} — 工具元数据（名称、描述、参数 Schema）</li>
 *   <li>{@link com.agentops.tools.ToolExecutor} — 工具执行契约（函数式接口）</li>
 *   <li>{@link com.agentops.tools.ToolResult} — 工具执行结果（成功/失败）</li>
 *   <li>{@link com.agentops.tools.ToolRegistry} — 工具注册中心 SPI</li>
 * </ul>
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@link com.agentops.tools.InMemoryToolRegistry} — 内存实现（开发/测试用）</li>
 * </ul>
 */
package com.agentops.tools;
