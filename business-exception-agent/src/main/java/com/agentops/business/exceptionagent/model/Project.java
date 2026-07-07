package com.agentops.business.exceptionagent.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 检测项目 — 被 Business Exception Agent 监控的 Java 应用配置。
 *
 * <p>每个项目拥有独立的 Git 仓库、日志源和可选工具集。
 * 诊断请求可通过 {@code projectId} 关联到特定项目，自动使用
 * 该项目的 Git 上下文、日志数据和启用工具。
 *
 * @param id               唯一标识（UUID）
 * @param name             项目名称（如 "order-service"）
 * @param description      项目描述（可选）
 * @param gitRepoUrl       远程 Git 仓库地址（可选，记录用）
 * @param gitRepoLocalPath 本地 Git 仓库路径（GitTool 工作目录）
 * @param enabledTools     启用的工具名列表（如 ["git-log", "log-search"]）
 * @param logSourceIds     关联的日志源 ID 列表
 * @param createdAt        创建时间
 * @param updatedAt        最后更新时间
 */
public record Project(
        String id,
        String name,
        String description,
        String gitRepoUrl,
        String gitRepoLocalPath,
        List<String> enabledTools,
        List<String> logSourceIds,
        Instant createdAt,
        Instant updatedAt) {

    public Project {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        enabledTools = enabledTools != null
                ? Collections.unmodifiableList(enabledTools)
                : Collections.emptyList();
        logSourceIds = logSourceIds != null
                ? Collections.unmodifiableList(logSourceIds)
                : Collections.emptyList();
    }

    /** 创建新项目时使用的工厂方法（自动生成时间戳） */
    public static Project create(String id, String name, String description,
                                  String gitRepoUrl, String gitRepoLocalPath) {
        Instant now = Instant.now();
        return new Project(id, name, description, gitRepoUrl, gitRepoLocalPath,
                List.of(), List.of(), now, now);
    }

    /** 基于现有项目创建更新副本（保留 id，更新其他字段） */
    public Project withUpdate(String name, String description,
                               String gitRepoUrl, String gitRepoLocalPath,
                               List<String> enabledTools, List<String> logSourceIds) {
        return new Project(id,
                name != null ? name : this.name,
                description != null ? description : this.description,
                gitRepoUrl != null ? gitRepoUrl : this.gitRepoUrl,
                gitRepoLocalPath != null ? gitRepoLocalPath : this.gitRepoLocalPath,
                enabledTools != null ? enabledTools : this.enabledTools,
                logSourceIds != null ? logSourceIds : this.logSourceIds,
                createdAt,
                Instant.now());
    }

    /** 判断指定工具是否已启用 */
    public boolean isToolEnabled(String toolName) {
        return enabledTools.contains(toolName);
    }
}
