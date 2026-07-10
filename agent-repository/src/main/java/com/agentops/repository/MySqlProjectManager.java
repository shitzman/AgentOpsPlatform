package com.agentops.repository;

import com.agentops.repository.entity.LogSourceEntity;
import com.agentops.repository.entity.ProjectEntity;
import com.agentops.repository.mapper.LogSourceMapper;
import com.agentops.repository.mapper.ProjectMapper;
import com.agentops.tools.core.FilteredToolRegistry;
import com.agentops.tools.core.InMemoryToolRegistry;
import com.agentops.tools.core.ToolDefinition;
import com.agentops.tools.core.ToolRegistry;
import com.agentops.tools.git.GitTool;
import com.agentops.tools.log.LogProvider;
import com.agentops.tools.log.LogProviderRegistry;
import com.agentops.tools.log.LogSourceConfig;
import com.agentops.tools.log.LogSourceType;
import com.agentops.tools.log.LogTool;
import com.agentops.tools.source.SourceCodeTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 项目管理器（MySQL 版）— Project / LogSource CRUD 和工具编排核心服务。
 *
 * <p>V1 重写：基于 MyBatis-Plus Mapper 直接操作 MySQL，不再通过 MemoryStore
 * 序列化 JSON 存储。所有数据持久化到 MySQL，应用重启不丢失。
 *
 * <p>职责：
 * <ul>
 *   <li>Project CRUD（projects 表）</li>
 *   <li>LogSource CRUD（log_sources 表）</li>
 *   <li>工具启用/禁用管理</li>
 *   <li>buildProjectToolRegistry() — 为指定项目构建专属工具注册表</li>
 * </ul>
 */
public class MySqlProjectManager {

    private final ProjectMapper projectMapper;
    private final LogSourceMapper logSourceMapper;
    private final ToolRegistry globalToolRegistry;
    private final LogProviderRegistry logProviderRegistry;
    private final ObjectMapper objectMapper;

    public MySqlProjectManager(ProjectMapper projectMapper,
                               LogSourceMapper logSourceMapper,
                               ToolRegistry globalToolRegistry,
                               LogProviderRegistry logProviderRegistry) {
        this.projectMapper = projectMapper;
        this.logSourceMapper = logSourceMapper;
        this.globalToolRegistry = globalToolRegistry;
        this.logProviderRegistry = logProviderRegistry;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    // ========================================================================
    // Project CRUD
    // ========================================================================

    /** 创建新项目 */
    public ProjectEntity createProject(String name, String description,
                                       String gitRepoUrl, String gitRepoLocalPath) {
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        ProjectEntity entity = new ProjectEntity(
                id, name, description, gitRepoUrl, gitRepoLocalPath,
                "[]", "[]", now, now);
        projectMapper.insert(entity);
        return entity;
    }

    /** 列出所有项目 */
    public List<ProjectEntity> listProjects() {
        return projectMapper.selectList(null);
    }

    /** 按 ID 获取单个项目 */
    public Optional<ProjectEntity> getProject(String id) {
        return Optional.ofNullable(projectMapper.selectById(id));
    }

    /** 更新项目（仅更新传入的非 null 字段） */
    public Optional<ProjectEntity> updateProject(String id, Map<String, Object> updates) {
        ProjectEntity existing = projectMapper.selectById(id);
        if (existing == null) return Optional.empty();

        if (updates.containsKey("name"))
            existing.setName((String) updates.get("name"));
        if (updates.containsKey("description"))
            existing.setDescription((String) updates.get("description"));
        if (updates.containsKey("gitRepoUrl"))
            existing.setGitRepoUrl((String) updates.get("gitRepoUrl"));
        if (updates.containsKey("gitRepoLocalPath"))
            existing.setGitRepoLocalPath((String) updates.get("gitRepoLocalPath"));
        if (updates.containsKey("enabledTools")) {
            try {
                Object val = updates.get("enabledTools");
                existing.setEnabledTools(objectMapper.writeValueAsString(val));
            } catch (JsonProcessingException ignored) {}
        }
        if (updates.containsKey("logSourceIds")) {
            try {
                Object val = updates.get("logSourceIds");
                existing.setLogSourceIds(objectMapper.writeValueAsString(val));
            } catch (JsonProcessingException ignored) {}
        }

        existing.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(existing);
        return Optional.of(existing);
    }

    /** 删除项目及其关联的日志源 */
    public boolean deleteProject(String id) {
        ProjectEntity project = projectMapper.selectById(id);
        if (project == null) return false;

        // 级联删除关联的日志源
        List<String> logSourceIds = parseJsonArray(project.getLogSourceIds());
        for (String lsId : logSourceIds) {
            logSourceMapper.deleteById(lsId);
        }

        return projectMapper.deleteById(id) > 0;
    }

    // ========================================================================
    // Tool 管理
    // ========================================================================

    /** 设置项目启用的工具列表 */
    public Optional<ProjectEntity> enableTools(String projectId, List<String> toolNames) {
        try {
            String json = objectMapper.writeValueAsString(toolNames);
            return updateProject(projectId, Map.of("enabledTools", json));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** 获取全局所有可用工具的定义列表（供 UI 展示） */
    public List<ToolDefinition> listAvailableTools() {
        return globalToolRegistry.listDefinitions();
    }

    /** 列出所有已知工具名称（从全局注册表读取，含描述） */
    public List<Map<String, String>> listAvailableToolNames() {
        return globalToolRegistry.listDefinitions().stream()
                .map(td -> Map.of("name", td.name(), "description", td.description()))
                .toList();
    }

    // ========================================================================
    // LogSource CRUD
    // ========================================================================

    /** 为项目添加日志源，返回 LogSourceConfig（兼容 API 层） */
    public LogSourceConfig addLogSource(String projectId, String name,
                                        LogSourceType type, Map<String, String> properties) {
        String id = UUID.randomUUID().toString();
        String propsJson;
        try {
            propsJson = objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            propsJson = "{}";
        }

        LogSourceEntity entity = new LogSourceEntity(
                id, projectId, name, type.name(), propsJson, true, LocalDateTime.now());
        logSourceMapper.insert(entity);

        // 关联到项目
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project != null) {
            List<String> currentIds = parseJsonArray(project.getLogSourceIds());
            currentIds.add(id);
            try {
                project.setLogSourceIds(objectMapper.writeValueAsString(currentIds));
            } catch (JsonProcessingException ignored) {}
            project.setUpdatedAt(LocalDateTime.now());
            projectMapper.updateById(project);
        }

        return entityToConfig(entity);
    }

    /** 获取项目的所有日志源 */
    public List<LogSourceConfig> getLogSources(String projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) return List.of();

        return parseJsonArray(project.getLogSourceIds()).stream()
                .map(logSourceMapper::selectById)
                .filter(Objects::nonNull)
                .map(this::entityToConfig)
                .toList();
    }

    /** 更新日志源 */
    public Optional<LogSourceConfig> updateLogSource(String logSourceId, Map<String, Object> updates) {
        LogSourceEntity entity = logSourceMapper.selectById(logSourceId);
        if (entity == null) return Optional.empty();

        if (updates.containsKey("name"))
            entity.setName((String) updates.get("name"));
        if (updates.containsKey("type"))
            entity.setType((String) updates.get("type"));
        if (updates.containsKey("enabled"))
            entity.setEnabled((Boolean) updates.get("enabled"));
        if (updates.containsKey("properties")) {
            try {
                entity.setProperties(objectMapper.writeValueAsString(updates.get("properties")));
            } catch (JsonProcessingException ignored) {}
        }

        logSourceMapper.updateById(entity);
        return Optional.of(entityToConfig(entity));
    }

    /** 删除日志源并从项目中移除关联 */
    public boolean deleteLogSource(String projectId, String logSourceId) {
        // 从项目中移除关联
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project != null) {
            List<String> currentIds = parseJsonArray(project.getLogSourceIds());
            currentIds.remove(logSourceId);
            try {
                project.setLogSourceIds(objectMapper.writeValueAsString(currentIds));
            } catch (JsonProcessingException ignored) {}
            project.setUpdatedAt(LocalDateTime.now());
            projectMapper.updateById(project);
        }

        return logSourceMapper.deleteById(logSourceId) > 0;
    }

    // ========================================================================
    // 核心方法：构建项目专属工具注册表
    // ========================================================================

    /**
     * 为指定项目构建一个独立的 ToolRegistry。
     *
     * <p>该注册表只包含项目启用的工具，每个工具绑定了项目的特定配置：
     * <ul>
     *   <li>Git 工具使用项目的 {@code gitRepoLocalPath}</li>
     *   <li>Log 工具使用项目的第一个启用的日志源</li>
     * </ul>
     *
     * @param projectId 项目 ID
     * @return 项目专属的工具注册表
     * @throws IllegalArgumentException 如果项目不存在
     */
    public ToolRegistry buildProjectToolRegistry(String projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
        }

        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        Set<String> enabled = new HashSet<>(parseJsonArray(project.getEnabledTools()));

        // Git 工具（如果启用且配置了本地路径）
        if (project.getGitRepoLocalPath() != null && !project.getGitRepoLocalPath().isBlank()) {
            GitTool git = new GitTool(project.getGitRepoLocalPath());
            if (enabled.contains("git-log"))
                registry.register(GitTool.logDefinition(), git.logExecutor());
            if (enabled.contains("git-blame"))
                registry.register(GitTool.blameDefinition(), git.blameExecutor());
            if (enabled.contains("git-show"))
                registry.register(GitTool.showDefinition(), git.showExecutor());
        }

        // Log 工具（如果启用且有可用的日志源）
        if (enabled.contains("log-search")) {
            List<LogSourceConfig> sources = getLogSources(projectId);
            LogSourceConfig activeSource = sources.stream()
                    .filter(LogSourceConfig::enabled)
                    .findFirst()
                    .orElse(null);

            if (activeSource != null) {
                LogProvider provider = logProviderRegistry.get(activeSource.type())
                        .orElse(null);
                if (provider != null) {
                    LogTool logTool = new LogTool(provider, activeSource);
                    registry.register(LogTool.definition(), logTool.executor());
                }
            }
        }

        return registry;
    }

    // ========================================================================
    // 实体 → 领域对象转换
    // ========================================================================

    /** LogSourceEntity → LogSourceConfig */
    private LogSourceConfig entityToConfig(LogSourceEntity entity) {
        LogSourceType type;
        try {
            type = LogSourceType.valueOf(entity.getType());
        } catch (IllegalArgumentException e) {
            type = LogSourceType.TEXT_INPUT;
        }

        Map<String, String> properties = parseJsonMap(entity.getProperties());
        Instant createdAt = entity.getCreatedAt() != null
                ? entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : Instant.now();

        return new LogSourceConfig(entity.getId(), entity.getName(), type,
                properties, entity.getEnabled() != null && entity.getEnabled(), createdAt);
    }

    // ========================================================================
    // JSON 解析辅助方法
    // ========================================================================

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
