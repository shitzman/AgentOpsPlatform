/**
 * Git 分析工具 — 通过本地 git 命令行提供代码仓库分析能力。
 *
 * <h3>核心类型</h3>
 * <ul>
 *   <li>{@link com.agentops.tools.git.GitTool} — git-log / git-blame / git-show 三种工具</li>
 *   <li>{@link com.agentops.tools.git.GitContext} — Git 仓库状态快照（分支、提交、Blame）</li>
 *   <li>{@link com.agentops.tools.git.GitContextProvider} — Git 上下文自动采集器</li>
 * </ul>
 */
package com.agentops.tools.git;
