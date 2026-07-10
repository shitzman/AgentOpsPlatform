/**
 * 工具层 — 所有外部能力都以 Tool 的形式封装。
 *
 * <h3>核心约束</h3>
 * 领域 Agent 禁止绕过 ToolRegistry 直接访问数据库、Git、日志、指标或任何外部系统。
 *
 * <h3>子包结构</h3>
 * <ul>
 *   <li>{@code core} — 工具注册中心 SPI 与核心契约（ToolDefinition / ToolExecutor / ToolResult / ToolRegistry）</li>
 *   <li>{@code git} — Git 分析工具（git-log / git-blame / git-show）与 Git 上下文采集器</li>
 *   <li>{@code log} — 日志源抽象（LogProvider / LogSourceConfig）及其实现（ES / 文件 / 文本输入）</li>
 *   <li>{@code source} — 源码阅读工具（read-source）</li>
 *   <li>{@code env} — 运行环境采集器（JVM / OS 信息）</li>
 * </ul>
 */
package com.agentops.tools;
