package com.agentops.api.dto;

import java.util.Collections;
import java.util.Map;

/**
 * 日志源测试连接请求 DTO — 保存前验证配置参数是否可用。
 *
 * @param type       日志源类型字符串（必填，对应 {@code LogSourceType} 枚举名）
 * @param properties 类型相关的连接参数
 */
public record LogSourceTestRequest(
        String type,
        Map<String, String> properties) {

    public LogSourceTestRequest {
        properties = properties != null
                ? Collections.unmodifiableMap(properties)
                : Collections.emptyMap();
    }
}
