package com.agentops.api.dto;

import java.util.Collections;
import java.util.Map;

/**
 * 添加日志源请求 DTO。
 *
 * @param name       日志源名称（必填，Service 层校验）
 * @param type        日志源类型字符串（必填，对应 {@code LogSourceType} 枚举名）
 * @param properties  类型相关的连接参数（可选）
 */
public record LogSourceCreateRequest(
        String name,
        String type,
        Map<String, String> properties) {

    public LogSourceCreateRequest {
        properties = properties != null
                ? Collections.unmodifiableMap(properties)
                : Collections.emptyMap();
    }
}
