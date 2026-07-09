package com.agentops.api.controller;

import com.agentops.api.dto.LogSourceCreateRequest;
import com.agentops.api.dto.LogSourceTestRequest;
import com.agentops.api.dto.LogSourceUpdateRequest;
import com.agentops.api.dto.LogFetchRequest;
import com.agentops.api.dto.ProjectContextRequest;
import com.agentops.api.dto.ProjectCreateRequest;
import com.agentops.api.dto.ProjectToolsUpdateRequest;
import com.agentops.api.dto.ProjectUpdateRequest;
import com.agentops.api.service.LogFetchService;
import com.agentops.api.service.LogSourceTestService;
import com.agentops.api.service.ProjectService;
import com.agentops.api.vo.LogFetchResultVo;
import com.agentops.api.vo.LogSourceListResponseVo;
import com.agentops.api.vo.LogSourceResponseVo;
import com.agentops.api.vo.LogSourceTestResultVo;
import com.agentops.api.vo.ProjectContextResponseVo;
import com.agentops.api.vo.ProjectListResponseVo;
import com.agentops.api.vo.ProjectResponseVo;
import com.agentops.api.vo.SimpleResultVo;
import com.agentops.api.vo.ToolListResponseVo;
import com.agentops.tools.LogSourceConfig;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * 项目管理 REST API — 检测项目的 CRUD、日志源管理和工具配置。
 *
 * <p>本类仅做 HTTP 适配：DTO 绑定与 VO 构造。业务逻辑（校验、DTO→Map 转换、
 * 上下文构建）由 {@link ProjectService} 承担。
 *
 * <p>端点分组：
 * <ul>
 *   <li><b>项目</b> — POST/GET/PUT/DELETE /api/projects</li>
 *   <li><b>工具</b> — GET/PUT /api/projects/{id}/tools + GET /api/tools</li>
 *   <li><b>日志源</b> — GET/POST/PUT/DELETE /api/projects/{id}/logsources</li>
 *   <li><b>上下文</b> — POST /api/projects/{id}/context</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class ProjectController {

    private final ProjectService projectService;
    private final LogSourceTestService logSourceTestService;
    private final LogFetchService logFetchService;

    public ProjectController(ProjectService projectService,
                             LogSourceTestService logSourceTestService,
                             LogFetchService logFetchService) {
        this.projectService = projectService;
        this.logSourceTestService = logSourceTestService;
        this.logFetchService = logFetchService;
    }

    // ========================================================================
    // 项目 CRUD
    // ========================================================================

    /** 创建项目 */
    @PostMapping("/projects")
    public ProjectResponseVo createProject(@RequestBody ProjectCreateRequest request) {
        try {
            return ProjectResponseVo.ok(projectService.createProject(request));
        } catch (Exception e) {
            return ProjectResponseVo.error(e.getMessage());
        }
    }

    /** 列出所有项目 */
    @GetMapping("/projects")
    public ProjectListResponseVo listProjects() {
        try {
            return ProjectListResponseVo.ok(projectService.listProjects());
        } catch (Exception e) {
            return ProjectListResponseVo.error(e.getMessage());
        }
    }

    /** 获取单个项目详情（含关联的日志源列表） */
    @GetMapping("/projects/{id}")
    public ProjectResponseVo getProject(@PathVariable String id) {
        try {
            Optional<ProjectService.ProjectDetail> detail = projectService.getProjectDetail(id);
            return detail
                    .map(d -> ProjectResponseVo.ok(d.project(), d.logSources()))
                    .orElseGet(() -> ProjectResponseVo.error("项目不存在: " + id));
        } catch (Exception e) {
            return ProjectResponseVo.error(e.getMessage());
        }
    }

    /** 更新项目 */
    @PutMapping("/projects/{id}")
    public ProjectResponseVo updateProject(@PathVariable String id,
                                           @RequestBody ProjectUpdateRequest request) {
        try {
            return projectService.updateProject(id, request)
                    .map(ProjectResponseVo::ok)
                    .orElseGet(() -> ProjectResponseVo.error("项目不存在: " + id));
        } catch (Exception e) {
            return ProjectResponseVo.error(e.getMessage());
        }
    }

    /** 删除项目（级联删除关联日志源） */
    @DeleteMapping("/projects/{id}")
    public SimpleResultVo deleteProject(@PathVariable String id) {
        try {
            return projectService.deleteProject(id)
                    ? SimpleResultVo.ok()
                    : SimpleResultVo.error("项目不存在: " + id);
        } catch (Exception e) {
            return SimpleResultVo.error(e.getMessage());
        }
    }

    // ========================================================================
    // 工具管理
    // ========================================================================

    /** 列出全局所有可用工具（供 UI 展示勾选框） */
    @GetMapping("/tools")
    public ToolListResponseVo listAvailableTools() {
        try {
            return ToolListResponseVo.ok(projectService.listAvailableTools());
        } catch (Exception e) {
            return ToolListResponseVo.error(e.getMessage());
        }
    }

    /** 设置项目启用的工具列表 */
    @PutMapping("/projects/{id}/tools")
    public ProjectResponseVo setProjectTools(@PathVariable String id,
                                             @RequestBody ProjectToolsUpdateRequest request) {
        try {
            return projectService.setProjectTools(id, request.toolNames())
                    .map(ProjectResponseVo::ok)
                    .orElseGet(() -> ProjectResponseVo.error("项目不存在: " + id));
        } catch (Exception e) {
            return ProjectResponseVo.error(e.getMessage());
        }
    }

    // ========================================================================
    // 日志源管理（挂载在项目下）
    // ========================================================================

    /** 列出项目的所有日志源 */
    @GetMapping("/projects/{id}/logsources")
    public LogSourceListResponseVo listLogSources(@PathVariable String id) {
        try {
            return LogSourceListResponseVo.ok(projectService.listLogSources(id));
        } catch (Exception e) {
            return LogSourceListResponseVo.error(e.getMessage());
        }
    }

    /** 添加日志源 */
    @PostMapping("/projects/{id}/logsources")
    public LogSourceResponseVo addLogSource(@PathVariable String id,
                                            @RequestBody LogSourceCreateRequest request) {
        try {
            LogSourceConfig source = projectService.addLogSource(id, request);
            return LogSourceResponseVo.ok(source);
        } catch (Exception e) {
            return LogSourceResponseVo.error(e.getMessage());
        }
    }

    /** 通过文件上传添加日志源（FILE_PATH 类型） */
    @PostMapping("/projects/{id}/logsources/upload")
    public LogSourceResponseVo uploadLogSource(@PathVariable String id,
                                               @RequestParam("name") String name,
                                               @RequestParam("file") MultipartFile file) {
        try {
            LogSourceConfig source = projectService.uploadLogSource(id, name, file);
            return LogSourceResponseVo.ok(source);
        } catch (Exception e) {
            return LogSourceResponseVo.error(e.getMessage());
        }
    }

    /** 测试日志源连通性（保存前调用，不依赖项目） */
    @PostMapping("/logsources/test")
    public LogSourceTestResultVo testLogSource(@RequestBody LogSourceTestRequest request) {
        try {
            return logSourceTestService.test(request);
        } catch (Exception e) {
            return LogSourceTestResultVo.error(e.getMessage());
        }
    }

    /** 按日志源 ID 拉取日志内容（供工作台异常分析使用） */
    @PostMapping("/projects/{projectId}/logsources/{logSourceId}/fetch")
    public LogFetchResultVo fetchLogSource(@PathVariable String projectId,
                                           @PathVariable String logSourceId,
                                           @RequestBody(required = false) LogFetchRequest request) {
        try {
            String keyword = request != null ? request.keyword() : null;
            Integer limit = request != null ? request.limit() : null;
            return logFetchService.fetch(projectId, logSourceId, keyword, limit);
        } catch (Exception e) {
            return LogFetchResultVo.error(e.getMessage());
        }
    }

    /** 更新日志源 */
    @PutMapping("/projects/{projectId}/logsources/{logSourceId}")
    public LogSourceResponseVo updateLogSource(@PathVariable String projectId,
                                               @PathVariable String logSourceId,
                                               @RequestBody LogSourceUpdateRequest request) {
        try {
            return projectService.updateLogSource(logSourceId, request)
                    .map(LogSourceResponseVo::ok)
                    .orElseGet(() -> LogSourceResponseVo.error("日志源不存在: " + logSourceId));
        } catch (Exception e) {
            return LogSourceResponseVo.error(e.getMessage());
        }
    }

    /** 删除日志源 */
    @DeleteMapping("/projects/{projectId}/logsources/{logSourceId}")
    public SimpleResultVo deleteLogSource(@PathVariable String projectId,
                                          @PathVariable String logSourceId) {
        try {
            return projectService.deleteLogSource(projectId, logSourceId)
                    ? SimpleResultVo.ok()
                    : SimpleResultVo.error("日志源不存在: " + logSourceId);
        } catch (Exception e) {
            return SimpleResultVo.error(e.getMessage());
        }
    }

    // ========================================================================
    // 项目上下文快照
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
    public ProjectContextResponseVo getProjectContext(@PathVariable String id,
                                                      @RequestBody ProjectContextRequest request) {
        try {
            return ProjectContextResponseVo.ok(projectService.getProjectContext(id, request.logContent()));
        } catch (Exception e) {
            return ProjectContextResponseVo.error(e.getMessage());
        }
    }
}
