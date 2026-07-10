/**
 * 工具注册中心核心契约 — SPI 接口与基础实现。
 *
 * <p>定义了 Tool（工具）概念的完整生命周期：定义 → 注册 → 执行 → 结果。
 *
 * <h3>核心类型</h3>
 * <ul>
 *   <li>{@link com.agentops.tools.core.ToolDefinition} — 工具元数据（名称、描述、参数 Schema）</li>
 *   <li>{@link com.agentops.tools.core.ToolExecutor} — 工具执行契约（函数式接口）</li>
 *   <li>{@link com.agentops.tools.core.ToolResult} — 工具执行结果（成功/失败）</li>
 *   <li>{@link com.agentops.tools.core.ToolRegistry} — 工具注册中心 SPI</li>
 *   <li>{@link com.agentops.tools.core.InMemoryToolRegistry} — 内存实现（线程安全）</li>
 *   <li>{@link com.agentops.tools.core.FilteredToolRegistry} — 装饰器，按名称过滤</li>
 * </ul>
 */
package com.agentops.tools.core;
