/**
 * 运行环境采集 — 收集 JVM 和操作系统的运行环境数据。
 *
 * <p>通过 Java ManagementFactory 获取 JVM 参数、内存、CPU 和 OS 信息，
 * 注入诊断上下文帮助 LLM 判断环境相关因素。
 *
 * <h3>核心类型</h3>
 * <ul>
 *   <li>{@link com.agentops.tools.env.EnvironmentInfo} — 运行环境快照（record）</li>
 *   <li>{@link com.agentops.tools.env.EnvironmentCollector} — 采集器（静态方法）</li>
 * </ul>
 */
package com.agentops.tools.env;
