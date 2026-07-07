package com.agentops.business.exceptionagent;

import com.agentops.business.exceptionagent.model.Project;
import com.agentops.memory.MemoryEntry;
import com.agentops.memory.MemoryStore;
import com.agentops.tools.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.*;

/**
 * 项目管理器 — 检测项目的 CRUD 和工具编排核心服务。
 *
 * <p>职责：
 * <ul>
 *   <li>Project CRUD（持久化到 MemoryStore，type = "project"）</li>
 *   <li>LogSource CRUD（持久化到 MemoryStore，type = "logsource"）</li>
 *   <li>工具启用/禁用管理</li>
 *   <li>buildProjectToolRegistry() — 为指定项目构建专属工具注册表</li>
 * </ul>
 *
 * <p>所有数据以 JSON 字符串形式存储在 MemoryStore 中，后续可平滑
 * 切换到 MySQL 版 MemoryStore 实现。
 */
public class ProjectManager {

    private static final String TYPE_PROJECT = "project";
    private static final String TYPE_LOG_SOURCE = "logsource";

    private final MemoryStore memoryStore;
    private final ToolRegistry globalToolRegistry;
    private final LogProviderRegistry logProviderRegistry;
    private final ObjectMapper objectMapper;

    public ProjectManager(MemoryStore memoryStore,
                          ToolRegistry globalToolRegistry,
                          LogProviderRegistry logProviderRegistry) {
        this.memoryStore = memoryStore;
        this.globalToolRegistry = globalToolRegistry;
        this.logProviderRegistry = logProviderRegistry;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    // ========================================================================
    // Project CRUD
    // ========================================================================

    /** 创建新项目 */
    public Project createProject(String name, String description,
                                  String gitRepoUrl, String gitRepoLocalPath) {
        String id = UUID.randomUUID().toString();
        Project project = Project.create(id, name, description, gitRepoUrl, gitRepoLocalPath);
        persistProject(project);
        return project;
    }

    /** 列出所有项目 */
    public List<Project> listProjects() {
        return memoryStore.findByType(TYPE_PROJECT, 1000).stream()
                .map(this::deserializeProject)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 按 ID 获取单个项目 */
    public Optional<Project> getProject(String id) {
        return memoryStore.findById(id)
                .map(this::deserializeProject);
    }

    /** 更新项目（仅更新传入的非 null 字段） */
    public Optional<Project> updateProject(String id, Map<String, Object> updates) {
        return getProject(id).map(existing -> {
            String name = stringOrNull(updates, "name");
            String description = stringOrNull(updates, "description");
            String gitRepoUrl = stringOrNull(updates, "gitRepoUrl");
            String gitRepoLocalPath = stringOrNull(updates, "gitRepoLocalPath");

            @SuppressWarnings("unchecked")
            List<String> enabledTools = updates.containsKey("enabledTools")
                    ? (List<String>) updates.get("enabledTools")
                    : null;

            @SuppressWarnings("unchecked")
            List<String> logSourceIds = updates.containsKey("logSourceIds")
                    ? (List<String>) updates.get("logSourceIds")
                    : null;

            Project updated = existing.withUpdate(
                    name, description, gitRepoUrl, gitRepoLocalPath,
                    enabledTools, logSourceIds);
            persistProject(updated);
            return updated;
        });
    }

    /** 删除项目及其关联的日志源 */
    public boolean deleteProject(String id) {
        Optional<Project> project = getProject(id);
        if (project.isEmpty()) return false;

        // 级联删除关联的日志源
        for (String lsId : project.get().logSourceIds()) {
            memoryStore.deleteById(lsId);
        }

        return memoryStore.deleteById(id);
    }

    // ========================================================================
    // Tool 管理
    // ========================================================================

    /** 设置项目启用的工具列表 */
    public Optional<Project> enableTools(String projectId, List<String> toolNames) {
        return updateProject(projectId, Map.of("enabledTools", (Object) toolNames));
    }

    /** 获取全局所有可用工具的定义列表（供 UI 展示） */
    public List<ToolDefinition> listAvailableTools() {
        return globalToolRegistry.listDefinitions();
    }

    // ========================================================================
    // LogSource CRUD
    // ========================================================================

    /** 为项目添加日志源 */
    public LogSourceConfig addLogSource(String projectId, String name,
                                         LogSourceType type, Map<String, String> properties) {
        String id = UUID.randomUUID().toString();
        LogSourceConfig source = new LogSourceConfig(id, name, type, properties, true, Instant.now());
        persistLogSource(source);

        // 关联到项目
        getProject(projectId).ifPresent(project -> {
            List<String> newIds = new ArrayList<>(project.logSourceIds());
            newIds.add(id);
            updateProject(projectId, Map.of("logSourceIds", (Object) newIds));
        });

        return source;
    }

    /** 获取项目的所有日志源 */
    public List<LogSourceConfig> getLogSources(String projectId) {
        return getProject(projectId)
                .map(project -> project.logSourceIds().stream()
                        .map(id -> memoryStore.findById(id)
                                .map(this::deserializeLogSource)
                                .orElse(null))
                        .filter(Objects::nonNull)
                        .toList())
                .orElse(List.of());
    }

    /** 更新日志源 */
    public Optional<LogSourceConfig> updateLogSource(String logSourceId, Map<String, Object> updates) {
        return memoryStore.findById(logSourceId)
                .map(this::deserializeLogSource)
                .map(existing -> {
                    String name = stringOrNull(updates, "name");
                    String typeStr = stringOrNull(updates, "type");
                    LogSourceType type = typeStr != null ? LogSourceType.valueOf(typeStr) : existing.type();
                    boolean enabled = updates.containsKey("enabled")
                            ? (Boolean) updates.get("enabled")
                            : existing.enabled();

                    @SuppressWarnings("unchecked")
                    Map<String, String> properties = updates.containsKey("properties")
                            ? (Map<String, String>) updates.get("properties")
                            : existing.properties();

                    LogSourceConfig updated = new LogSourceConfig(
                            existing.id(),
                            name != null ? name : existing.name(),
                            type,
                            properties,
                            enabled,
                            existing.createdAt());
                    persistLogSource(updated);
                    return updated;
                });
    }

    /** 删除日志源并从项目中移除关联 */
    public boolean deleteLogSource(String projectId, String logSourceId) {
        // 从项目中移除关联
        getProject(projectId).ifPresent(project -> {
            List<String> newIds = new ArrayList<>(project.logSourceIds());
            newIds.remove(logSourceId);
            updateProject(projectId, Map.of("logSourceIds", (Object) newIds));
        });

        return memoryStore.deleteById(logSourceId);
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
        Project project = getProject(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));

        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        Set<String> enabled = new HashSet<>(project.enabledTools());

        // Git 工具（如果启用且配置了本地路径）
        if (project.gitRepoLocalPath() != null && !project.gitRepoLocalPath().isBlank()) {
            GitTool git = new GitTool(project.gitRepoLocalPath());
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

    /** 列出所有已知工具名称（从全局注册表读取，含描述） */
    public List<Map<String, String>> listAvailableToolNames() {
        return globalToolRegistry.listDefinitions().stream()
                .map(td -> Map.of("name", td.name(), "description", td.description()))
                .toList();
    }

    // ========================================================================
    // 序列化/反序列化助手
    // ========================================================================

    private void persistProject(Project project) {
        try {
            String json = objectMapper.writeValueAsString(project);
            memoryStore.save(new MemoryEntry(
                    project.id(), TYPE_PROJECT, json, Map.of(), project.updatedAt()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化项目失败: " + e.getMessage(), e);
        }
    }

    private Project deserializeProject(MemoryEntry entry) {
        try {
            return objectMapper.readValue(entry.content(), Project.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void persistLogSource(LogSourceConfig source) {
        try {
            String json = objectMapper.writeValueAsString(source);
            memoryStore.save(new MemoryEntry(
                    source.id(), TYPE_LOG_SOURCE, json, Map.of(), source.createdAt()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化日志源失败: " + e.getMessage(), e);
        }
    }

    private LogSourceConfig deserializeLogSource(MemoryEntry entry) {
        try {
            return objectMapper.readValue(entry.content(), LogSourceConfig.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }
}
