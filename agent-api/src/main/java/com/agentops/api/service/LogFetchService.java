package com.agentops.api.service;

import com.agentops.api.vo.LogFetchResultVo;
import com.agentops.repository.MySqlProjectManager;
import com.agentops.tools.LogProvider;
import com.agentops.tools.LogProviderRegistry;
import com.agentops.tools.LogSourceConfig;
import com.agentops.tools.ToolResult;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日志拉取服务 — 按日志源 ID 拉取日志内容，供异常分析使用。
 *
 * <p>从项目的日志源中按 ID 查找配置，分派到对应的 {@link LogProvider#search} 执行查询，
 * 返回日志文本。结构对称于 {@link LogSourceTestService}（同样按 type 分派到 Provider）。
 */
@Service
public class LogFetchService {

    private static final int DEFAULT_LIMIT = 200;

    private final MySqlProjectManager projectManager;
    private final LogProviderRegistry logProviderRegistry;

    public LogFetchService(MySqlProjectManager projectManager,
                           LogProviderRegistry logProviderRegistry) {
        this.projectManager = projectManager;
        this.logProviderRegistry = logProviderRegistry;
    }

    /**
     * 拉取指定日志源的日志内容。
     *
     * @param projectId   项目 ID
     * @param logSourceId 日志源 ID
     * @param keyword     关键词过滤（可选，为空时拉取全部受 limit 限制）
     * @param limit       最大返回行数（为空时默认 200）
     * @return 拉取结果（成功时含日志文本和行数，失败时含原因）
     */
    public LogFetchResultVo fetch(String projectId, String logSourceId, String keyword, Integer limit) {
        LogSourceConfig config = findLogSource(projectId, logSourceId);
        if (config == null) {
            return LogFetchResultVo.error("日志源不存在: " + logSourceId);
        }

        LogProvider provider = logProviderRegistry.get(config.type()).orElse(null);
        if (provider == null) {
            return LogFetchResultVo.error("不支持的日志源类型: " + config.type());
        }

        Map<String, Object> args = buildSearchArgs(keyword, limit);
        ToolResult result = provider.search(args, config);
        return toVo(result);
    }

    /** 在项目的日志源列表中按 ID 查找配置 */
    private LogSourceConfig findLogSource(String projectId, String logSourceId) {
        return projectManager.getLogSources(projectId).stream()
                .filter(ls -> ls.id().equals(logSourceId))
                .findFirst()
                .orElse(null);
    }

    /** 构造 LogProvider.search 所需的参数 Map */
    private static Map<String, Object> buildSearchArgs(String keyword, Integer limit) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (keyword != null && !keyword.isBlank()) {
            args.put("keyword", keyword);
        }
        args.put("limit", limit != null ? limit : DEFAULT_LIMIT);
        return args;
    }

    /** 将 ToolResult 映射为拉取结果 VO（成功时统计行数） */
    private static LogFetchResultVo toVo(ToolResult result) {
        if (!result.success()) {
            return LogFetchResultVo.error(result.error());
        }
        String content = result.output() != null ? result.output() : "";
        int lineCount = content.isEmpty() ? 0 : content.split("\\r?\\n").length;
        return LogFetchResultVo.ok(content, lineCount);
    }
}
