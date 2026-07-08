package com.agentops.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 日志源实体 — 映射 log_sources 表。
 *
 * <p>每个项目可以有多个日志源，类型包括 TEXT_INPUT / FILE_PATH / ELASTICSEARCH。
 * properties 以 JSON 字符串存储，包含类型相关的连接参数。
 */
@TableName("log_sources")
public class LogSourceEntity {

    @TableId
    private String id;
    private String projectId;
    private String name;
    /** LogSourceType 枚举名称 */
    private String type;
    /** JSON 字符串，如 {"filePath": "/var/log/app.log"} */
    private String properties;
    private Boolean enabled;
    private LocalDateTime createdAt;

    public LogSourceEntity() {}

    public LogSourceEntity(String id, String projectId, String name, String type,
                           String properties, Boolean enabled, LocalDateTime createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.type = type;
        this.properties = properties;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    // ---- getters ----

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getProperties() { return properties; }
    public Boolean getEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ---- setters ----

    public void setId(String id) { this.id = id; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setProperties(String properties) { this.properties = properties; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
