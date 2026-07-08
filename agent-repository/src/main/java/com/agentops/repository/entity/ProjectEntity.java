package com.agentops.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 项目实体 — 映射 projects 表。
 *
 * <p>每个项目代表一个被监控的 Java 应用，拥有独立的 Git 仓库、日志源和可选工具集。
 */
@TableName("projects")
public class ProjectEntity {

    @TableId
    private String id;
    private String name;
    private String description;
    private String gitRepoUrl;
    private String gitRepoLocalPath;
    /** JSON 数组字符串，如 ["git-log","log-search"] */
    private String enabledTools;
    /** JSON 数组字符串，如 ["uuid-1","uuid-2"] */
    private String logSourceIds;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public ProjectEntity() {}

    public ProjectEntity(String id, String name, String description,
                         String gitRepoUrl, String gitRepoLocalPath,
                         String enabledTools, String logSourceIds,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.gitRepoUrl = gitRepoUrl;
        this.gitRepoLocalPath = gitRepoLocalPath;
        this.enabledTools = enabledTools;
        this.logSourceIds = logSourceIds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ---- getters ----

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getGitRepoUrl() { return gitRepoUrl; }
    public String getGitRepoLocalPath() { return gitRepoLocalPath; }
    public String getEnabledTools() { return enabledTools; }
    public String getLogSourceIds() { return logSourceIds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // ---- setters ----

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setGitRepoUrl(String gitRepoUrl) { this.gitRepoUrl = gitRepoUrl; }
    public void setGitRepoLocalPath(String gitRepoLocalPath) { this.gitRepoLocalPath = gitRepoLocalPath; }
    public void setEnabledTools(String enabledTools) { this.enabledTools = enabledTools; }
    public void setLogSourceIds(String logSourceIds) { this.logSourceIds = logSourceIds; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
