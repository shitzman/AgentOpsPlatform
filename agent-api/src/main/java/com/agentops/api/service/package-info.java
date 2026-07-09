/**
 * 应用服务层 — 承载从 Controller 抽取的业务逻辑。
 *
 * <p>每个 {@code @Service} 类负责一个业务领域：
 * <ul>
 *   <li>{@link com.agentops.api.service.DiagnosisService} — 诊断与追问编排</li>
 *   <li>{@link com.agentops.api.service.ConversationService} — 对话历史加载与持久化</li>
 *   <li>{@link com.agentops.api.service.DiagnosisReportPersistenceService} — 诊断报告持久化与历史查询</li>
 *   <li>{@link com.agentops.api.service.ProjectService} — 项目/日志源/工具管理</li>
 * </ul>
 *
 * <p>Service 之间通过构造器注入协作，Controller 仅做 HTTP 适配（DTO 绑定、Span 管理、VO 构造）。
 */
package com.agentops.api.service;
