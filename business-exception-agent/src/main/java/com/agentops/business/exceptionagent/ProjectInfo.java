package com.agentops.business.exceptionagent;

/**
 * 项目信息载体 — 由 delivery 层解析后传入 {@link DiagnosisOrchestrator}，
 * 避免领域层依赖 {@code agent-repository} 的 {@code ProjectEntity}。
 *
 * @param id          项目 ID
 * @param name        项目名称
 * @param description 项目描述
 * @param repoPath    Git 仓库本地路径（用于 Git context 采集）
 */
public record ProjectInfo(String id, String name, String description, String repoPath) {
}
