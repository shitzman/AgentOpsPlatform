package com.agentops.api.service;

import com.agentops.api.dto.LogSourceCreateRequest;
import com.agentops.api.dto.LogSourceUpdateRequest;
import com.agentops.api.dto.ProjectCreateRequest;
import com.agentops.api.dto.ProjectUpdateRequest;
import com.agentops.api.vo.ToolInfoVo;
import com.agentops.business.exceptionagent.model.DiagnosisContext;
import com.agentops.repository.MySqlProjectManager;
import com.agentops.repository.entity.ProjectEntity;
import com.agentops.tools.EnvironmentCollector;
import com.agentops.tools.EnvironmentInfo;
import com.agentops.tools.GitContext;
import com.agentops.tools.GitContextProvider;
import com.agentops.tools.LogExtractor;
import com.agentops.tools.LogSourceConfig;
import com.agentops.tools.LogSourceType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 项目管理服务 — 封装 {@link MySqlProjectManager} 的 DTO/VO 转换、校验与上下文构建。
 *
 * <p>从 {@code ProjectController} 抽取，使 Controller 只做 HTTP 适配。{@code MySqlProjectManager}
 * 的 API 保持不变（仍接收 Map 做部分更新），DTO→Map 转换在本服务边界完成。
 */
@Service
public class ProjectService {

    private final MySqlProjectManager projectManager;
    private final LogFileStorage logFileStorage;

    public ProjectService(MySqlProjectManager projectManager, LogFileStorage logFileStorage) {
        this.projectManager = projectManager;
        this.logFileStorage = logFileStorage;
    }

    // ========================================================================
    // 项目 CRUD
    // ========================================================================

    /** 创建项目 */
    public ProjectEntity createProject(ProjectCreateRequest request) {
        requireNonBlank(request.name(), "缺少 name 字段");
        String gitRepoLocalPath = (request.gitRepoLocalPath() == null || request.gitRepoLocalPath().isBlank())
                ? System.getProperty("user.dir")
                : request.gitRepoLocalPath();
        return projectManager.createProject(
                request.name(),
                request.description(),
                request.gitRepoUrl(),
                gitRepoLocalPath);
    }

    /** 列出所有项目 */
    public List<ProjectEntity> listProjects() {
        return projectManager.listProjects();
    }

    /** 获取项目详情（含关联日志源） */
    public Optional<ProjectDetail> getProjectDetail(String id) {
        return projectManager.getProject(id).map(project ->
                new ProjectDetail(project, projectManager.getLogSources(id)));
    }

    /** 更新项目（仅更新非 null 字段） */
    public Optional<ProjectEntity> updateProject(String id, ProjectUpdateRequest request) {
        return projectManager.updateProject(id, toUpdateMap(request));
    }

    /** 删除项目（级联删除关联日志源） */
    public boolean deleteProject(String id) {
        return projectManager.deleteProject(id);
    }

    // ========================================================================
    // 工具管理
    // ========================================================================

    /** 列出全局可用工具 */
    public List<ToolInfoVo> listAvailableTools() {
        return projectManager.listAvailableToolNames().stream()
                .map(m -> new ToolInfoVo(m.get("name"), m.get("description")))
                .toList();
    }

    /** 设置项目启用的工具列表 */
    public Optional<ProjectEntity> setProjectTools(String id, List<String> toolNames) {
        return projectManager.enableTools(id, toolNames);
    }

    // ========================================================================
    // 日志源管理
    // ========================================================================

    /** 列出项目的所有日志源 */
    public List<LogSourceConfig> listLogSources(String projectId) {
        return projectManager.getLogSources(projectId);
    }

    /** 添加日志源 */
    public LogSourceConfig addLogSource(String projectId, LogSourceCreateRequest request) {
        requireNonBlank(request.name(), "缺少 name 或 type 字段");
        requireNonBlank(request.type(), "缺少 name 或 type 字段");
        LogSourceType type = parseLogSourceType(request.type());
        return projectManager.addLogSource(projectId, request.name(), type, request.properties());
    }

    /** 通过文件上传创建 FILE_PATH 日志源 */
    public LogSourceConfig uploadLogSource(String projectId, String name, MultipartFile file) {
        requireNonBlank(name, "缺少 name 字段");
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String savedPath;
        try {
            savedPath = logFileStorage.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("保存上传文件失败: " + e.getMessage(), e);
        }

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("filePath", savedPath);
        properties.put("originalFileName", file.getOriginalFilename());

        return projectManager.addLogSource(projectId, name, LogSourceType.FILE_PATH, properties);
    }

    /** 更新日志源 */
    public Optional<LogSourceConfig> updateLogSource(String logSourceId, LogSourceUpdateRequest request) {
        return projectManager.updateLogSource(logSourceId, toUpdateMap(request));
    }

    /** 删除日志源（同时清理上传的文件） */
    public boolean deleteLogSource(String projectId, String logSourceId) {
        // 清理上传的文件（通过 originalFileName 属性区分上传文件 vs 手动路径）
        projectManager.getLogSources(projectId).stream()
                .filter(ls -> ls.id().equals(logSourceId))
                .findFirst()
                .ifPresent(this::cleanupUploadedFile);

        return projectManager.deleteLogSource(projectId, logSourceId);
    }

    /** 删除上传文件（仅对含 originalFileName 属性的日志源生效） */
    private void cleanupUploadedFile(LogSourceConfig source) {
        String originalFileName = source.property("originalFileName", "");
        if (!originalFileName.isBlank()) {
            logFileStorage.delete(source.property("filePath", ""));
        }
    }

    // ========================================================================
    // 项目上下文快照
    // ========================================================================

    /**
     * 聚合项目的环境、Git、日志数据为诊断上下文。
     *
     * @param projectId  项目 ID
     * @param logContent 原始日志文本（可选）
     * @return 诊断上下文
     * @throws IllegalArgumentException 项目不存在时
     */
    public DiagnosisContext getProjectContext(String projectId, String logContent) {
        ProjectEntity project = projectManager.getProject(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));

        EnvironmentInfo env = EnvironmentCollector.collect();
        GitContext gitCtx = collectGitContext(project.getGitRepoLocalPath());

        String stackTrace = null;
        String logContext = null;
        if (logContent != null && !logContent.isBlank()) {
            stackTrace = LogExtractor.extractStackTrace(logContent);
            if (stackTrace != null) {
                logContext = LogExtractor.extractLogContext(logContent, stackTrace);
            }
        }

        return new DiagnosisContext(
                project.getId(), project.getName(), project.getDescription(),
                stackTrace, null, logContext, gitCtx, env);
    }

    // ========================================================================
    // 私有辅助
    // ========================================================================

    private static GitContext collectGitContext(String repoPath) {
        if (repoPath == null || repoPath.isBlank()) return null;
        return new GitContextProvider(repoPath).collect(List.of());
    }

    /** 将项目更新 DTO 转为部分更新 Map（仅包含非 null 字段） */
    private static Map<String, Object> toUpdateMap(ProjectUpdateRequest request) {
        Map<String, Object> updates = new LinkedHashMap<>();
        if (request.name() != null) updates.put("name", request.name());
        if (request.description() != null) updates.put("description", request.description());
        if (request.gitRepoUrl() != null) updates.put("gitRepoUrl", request.gitRepoUrl());
        if (request.gitRepoLocalPath() != null) updates.put("gitRepoLocalPath", request.gitRepoLocalPath());
        if (request.enabledTools() != null) updates.put("enabledTools", request.enabledTools());
        if (request.logSourceIds() != null) updates.put("logSourceIds", request.logSourceIds());
        return updates;
    }

    /** 将日志源更新 DTO 转为部分更新 Map */
    private static Map<String, Object> toUpdateMap(LogSourceUpdateRequest request) {
        Map<String, Object> updates = new LinkedHashMap<>();
        if (request.name() != null) updates.put("name", request.name());
        if (request.type() != null) updates.put("type", request.type());
        if (request.enabled() != null) updates.put("enabled", request.enabled());
        if (!request.properties().isEmpty()) updates.put("properties", request.properties());
        return updates;
    }

    private static LogSourceType parseLogSourceType(String typeStr) {
        try {
            return LogSourceType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "无效的日志源类型: " + typeStr + "，可选: TEXT_INPUT, FILE_PATH, ELASTICSEARCH");
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /** 项目详情结果载体（不序列化） */
    public record ProjectDetail(ProjectEntity project, List<LogSourceConfig> logSources) {
    }
}
