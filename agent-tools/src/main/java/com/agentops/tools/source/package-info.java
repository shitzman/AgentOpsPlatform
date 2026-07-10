/**
 * 源码阅读工具 — 读取代码仓库中的源文件内容。
 *
 * <p>注册为 {@code read-source} 工具，LLM 可通过它读取堆栈中项目代码帧对应的源文件，
 * 理解异常发生位置的代码逻辑。是诊断流程的核心工具。
 *
 * <h3>核心类型</h3>
 * <ul>
 *   <li>{@link com.agentops.tools.source.SourceCodeTool} — read-source 工具（行号 + 范围过滤）</li>
 * </ul>
 */
package com.agentops.tools.source;
