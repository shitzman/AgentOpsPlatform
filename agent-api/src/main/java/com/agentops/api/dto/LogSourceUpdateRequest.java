package com.agentops.api.dto;

import java.util.Collections;
import java.util.Map;

/**
 * 更新日志源请求 DTO — 部分更新语义。
 *
 * @param name       日志源名称
 * @param type        日志源类型字符串（对应 {@code LogSourceType} 枚举名）
 * @param enabled     是否启用（boxed 以支持部分更新时省略）
 * @param properties  连接参数
 */
public record LogSourceUpdateRequest(
        String name,
        String type,
        Boolean enabled,
        Map<String, String> properties) {

    public LogSourceUpdateRequest {
        properties = properties != null
                ? Collections.unmodifiableMap(properties)
                : Collections.emptyMap();
    }
}
