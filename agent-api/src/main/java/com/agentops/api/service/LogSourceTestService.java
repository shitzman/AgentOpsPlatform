package com.agentops.api.service;

import com.agentops.api.dto.LogSourceTestRequest;
import com.agentops.api.vo.LogSourceTestResultVo;
import com.agentops.tools.core.ToolResult;
import com.agentops.tools.log.LogProvider;
import com.agentops.tools.log.LogProviderRegistry;
import com.agentops.tools.log.LogSourceConfig;
import com.agentops.tools.log.LogSourceType;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 日志源测试连接服务 — 保存前验证日志源配置是否可用。
 *
 * <p>按日志源类型分派到对应的 {@link LogProvider#test(LogSourceConfig)}：
 * <ul>
 *   <li>ELASTICSEARCH — HTTP GET 验证连通性和认证</li>
 *   <li>FILE_PATH — 验证文件存在且可读</li>
 *   <li>TEXT_INPUT — 验证 rawText 非空</li>
 * </ul>
 */
@Service
public class LogSourceTestService {

    private final LogProviderRegistry logProviderRegistry;

    public LogSourceTestService(LogProviderRegistry logProviderRegistry) {
        this.logProviderRegistry = logProviderRegistry;
    }

    /**
     * 测试日志源连通性。
     *
     * @param request 日志源配置（type + properties）
     * @return 测试结果
     */
    public LogSourceTestResultVo test(LogSourceTestRequest request) {
        requireNonBlank(request.type(), "缺少 type 字段");

        LogSourceType type = parseLogSourceType(request.type());

        LogSourceConfig config = new LogSourceConfig(
                "test", "test", type, request.properties(), true, Instant.now());

        return logProviderRegistry.get(type)
                .map(provider -> toVo(provider.test(config)))
                .orElseGet(() -> LogSourceTestResultVo.error("不支持的日志源类型: " + type));
    }

    /** 将 ToolResult 映射为测试结果 VO */
    private static LogSourceTestResultVo toVo(ToolResult result) {
        return result.success()
                ? LogSourceTestResultVo.ok(result.output())
                : LogSourceTestResultVo.error(result.error());
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
}
