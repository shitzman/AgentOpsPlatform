package com.agentops.api.controller;

import com.agentops.business.exceptionagent.model.DiagnosisContext;
import com.agentops.repository.MySqlProjectManager;
import com.agentops.repository.entity.ProjectEntity;
import com.agentops.tools.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 项目管理 REST API — 检测项目的 CRUD、日志源管理和工具配置。
 *
 * <p>V1 变更：使用 MySqlProjectManager（MySQL 持久化），ProjectEntity 替代旧 Project record。
 *
 * <p>端点分组：
 * <ul>
 *   <li><b>项目</b> — POST/GET/PUT/DELETE /api/projects</li>
 *   <li><b>工具</b> — GET/PUT /api/projects/{id}/tools + GET /api/tools</li>
 *   <li><b>日志源</b> — GET/POST/PUT/DELETE /api/projects/{id}/logsources</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class ProjectController {

    private final MySqlProjectManager projectManager;

    public ProjectController(MySqlProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    // ========================================================================
    // 项目 CRUD
    // ========================================================================

    /** 创建项目 */
    @PostMapping("/projects")
    public Map<String, Object> createProject(@RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            if (name == null || name.isBlank()) {
                return Map.of("success", false, "error", "缺少 name 字段");
            }

            ProjectEntity project = projectManager.createProject(
                    name,
                    body.getOrDefault("description", ""),
                    body.getOrDefault("gitRepoUrl", ""),
                    body.getOrDefault("gitRepoLocalPath", System.getProperty("user.dir"))
            );

            return Map.of("success", true, "project", project);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 列出所有项目 */
    @GetMapping("/projects")
    public Map<String, Object> listProjects() {
        try {
            List<ProjectEntity> projects = projectManager.listProjects();
            return Map.of("success", true, "projects", projects);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 获取单个项目详情（含关联的日志源列表） */
    @GetMapping("/projects/{id}")
    public Map<String, Object> getProject(@PathVariable String id) {
        try {
            Optional<ProjectEntity> project = projectManager.getProject(id);
            if (project.isEmpty()) {
                return Map.of("success", false, "error", "项目不存在: " + id);
            }
            List<LogSourceConfig> logSources = projectManager.getLogSources(id);
            return Map.of("success", true, "project", project.get(), "logSources", logSources);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 更新项目 */
    @PutMapping("/projects/{id}")
    public Map<String, Object> updateProject(@PathVariable String id,
                                              @RequestBody Map<String, Object> body) {
        try {
            Optional<ProjectEntity> updated = projectManager.updateProject(id, body);
            if (updated.isEmpty()) {
                return Map.of("success", false, "error", "项目不存在: " + id);
            }
            return Map.of("success", true, "project", updated.get());
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 删除项目（级联删除关联日志源） */
    @DeleteMapping("/projects/{id}")
    public Map<String, Object> deleteProject(@PathVariable String id) {
        try {
            boolean deleted = projectManager.deleteProject(id);
            if (!deleted) {
                return Map.of("success", false, "error", "项目不存在: " + id);
            }
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ========================================================================
    // 工具管理
    // ========================================================================

    /** 列出全局所有可用工具（供 UI 展示勾选框） */
    @GetMapping("/tools")
    public Map<String, Object> listAvailableTools() {
        try {
            return Map.of("success", true,
                    "tools", projectManager.listAvailableToolNames());
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 设置项目启用的工具列表 */
    @PutMapping("/projects/{id}/tools")
    public Map<String, Object> setProjectTools(@PathVariable String id,
                                                @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> toolNames = (List<String>) body.get("toolNames");
            if (toolNames == null) {
                return Map.of("success", false, "error", "缺少 toolNames 字段");
            }

            Optional<ProjectEntity> updated = projectManager.enableTools(id, toolNames);
            if (updated.isEmpty()) {
                return Map.of("success", false, "error", "项目不存在: " + id);
            }
            return Map.of("success", true, "project", updated.get());
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ========================================================================
    // 日志源管理（挂载在项目下）
    // ========================================================================

    /** 列出项目的所有日志源 */
    @GetMapping("/projects/{id}/logsources")
    public Map<String, Object> listLogSources(@PathVariable String id) {
        try {
            List<LogSourceConfig> sources = projectManager.getLogSources(id);
            return Map.of("success", true, "logSources", sources);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 添加日志源 */
    @PostMapping("/projects/{id}/logsources")
    public Map<String, Object> addLogSource(@PathVariable String id,
                                             @RequestBody Map<String, Object> body) {
        try {
            String name = stringField(body, "name");
            String typeStr = stringField(body, "type");
            if (name == null || typeStr == null) {
                return Map.of("success", false, "error", "缺少 name 或 type 字段");
            }

            LogSourceType type;
            try {
                type = LogSourceType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                return Map.of("success", false, "error",
                        "无效的日志源类型: " + typeStr + "，可选: TEXT_INPUT, FILE_PATH, ELASTICSEARCH");
            }

            @SuppressWarnings("unchecked")
            Map<String, String> properties = (Map<String, String>) body.getOrDefault("properties", Map.of());

            LogSourceConfig source = projectManager.addLogSource(id, name, type, properties);
            return Map.of("success", true, "logSource", source);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 更新日志源 */
    @PutMapping("/projects/{projectId}/logsources/{logSourceId}")
    public Map<String, Object> updateLogSource(@PathVariable String projectId,
                                                @PathVariable String logSourceId,
                                                @RequestBody Map<String, Object> body) {
        try {
            Optional<LogSourceConfig> updated = projectManager.updateLogSource(logSourceId, body);
            if (updated.isEmpty()) {
                return Map.of("success", false, "error", "日志源不存在: " + logSourceId);
            }
            return Map.of("success", true, "logSource", updated.get());
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 删除日志源 */
    @DeleteMapping("/projects/{projectId}/logsources/{logSourceId}")
    public Map<String, Object> deleteLogSource(@PathVariable String projectId,
                                                @PathVariable String logSourceId) {
        try {
            boolean deleted = projectManager.deleteLogSource(projectId, logSourceId);
            if (!deleted) {
                return Map.of("success", false, "error", "日志源不存在: " + logSourceId);
            }
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ========================================================================
    // 项目上下文快照 (Phase 2)
    // ========================================================================

    /**
     * 获取项目的完整上下文快照 — 聚合环境、Git、日志数据。
     *
     * <pre>
     * POST /api/projects/{id}/context
     * { "logContent": "(可选) 原始日志文本" }
     * </pre>
     */
    @PostMapping("/projects/{id}/context")
    public Map<String, Object> getProjectContext(@PathVariable String id,
                                                  @RequestBody Map<String, String> body) {
        try {
            Optional<ProjectEntity> projectOpt = projectManager.getProject(id);
            if (projectOpt.isEmpty()) {
                return Map.of("success", false, "error", "项目不存在: " + id);
            }
            ProjectEntity project = projectOpt.get();

            // 1. 采集运行环境
            EnvironmentInfo env = EnvironmentCollector.collect();

            // 2. 采集 Git 上下文
            GitContext gitCtx = null;
            String repoPath = project.getGitRepoLocalPath();
            if (repoPath != null && !repoPath.isBlank()) {
                GitContextProvider gitProvider = new GitContextProvider(repoPath);
                gitCtx = gitProvider.collect(List.of());
            }

            // 3. 提取日志中的堆栈和上下文
            String stackTrace = null;
            String logContext = null;
            String logContent = body.get("logContent");
            if (logContent != null && !logContent.isBlank()) {
                stackTrace = LogExtractor.extractStackTrace(logContent);
                if (stackTrace != null) {
                    logContext = LogExtractor.extractLogContext(logContent, stackTrace);
                }
            }

            // 4. 组装 DiagnosisContext
            DiagnosisContext ctx = new DiagnosisContext(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    stackTrace,
                    null,  // parsedStackTrace — 由诊断 Workflow 负责解析
                    logContext,
                    gitCtx,
                    env);

            return Map.of("success", true, "context", ctx);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private static String stringField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
