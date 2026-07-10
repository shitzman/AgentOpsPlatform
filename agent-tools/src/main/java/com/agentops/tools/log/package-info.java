/**
 * 日志源抽象层 — 可插拔的日志查询后端。
 *
 * <h3>核心类型</h3>
 * <ul>
 *   <li>{@link com.agentops.tools.log.LogTool} — log-search 工具（注册到 ToolRegistry）</li>
 *   <li>{@link com.agentops.tools.log.LogProvider} — 日志搜索提供者 SPI</li>
 *   <li>{@link com.agentops.tools.log.LogProviderRegistry} — 日志提供者注册表 SPI</li>
 *   <li>{@link com.agentops.tools.log.LogSourceConfig} — 日志源配置（连接参数 + 类型）</li>
 *   <li>{@link com.agentops.tools.log.LogSourceType} — 日志源类型枚举</li>
 *   <li>{@link com.agentops.tools.log.LogExtractor} — 日志异常堆栈提取器</li>
 * </ul>
 *
 * <h3>内置实现</h3>
 * <ul>
 *   <li>{@link com.agentops.tools.log.TextInputLogProvider} — UI 粘贴的原始文本</li>
 *   <li>{@link com.agentops.tools.log.FileLogProvider} — 服务器本地日志文件</li>
 *   <li>{@link com.agentops.tools.log.ElasticsearchLogProvider} — ES _search API（真实 HTTP 查询）</li>
 * </ul>
 */
package com.agentops.tools.log;
