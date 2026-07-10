package com.agentops.tools.log;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * 日志源配置 — 描述一个日志数据来源的连接参数和状态。
 *
 * <p>{@code properties} 存放类型相关的参数，不同 {@link LogSourceType}
 * 有各自的键名约定：
 * <ul>
 *   <li>{@code TEXT_INPUT}: {@code {"rawText": "日志文本内容"}}</li>
 *   <li>{@code FILE_PATH}: {@code {"filePath": "/var/log/app.log"}} 或上传文件时附 {@code "originalFileName": "app.log"}</li>
 *   <li>{@code ELASTICSEARCH}: {@code {"esUrl": "http://...", "index": "logs-*", "username": "...", "password": "...", "apiKey": "..."}}（认证可选，Basic Auth 与 API Key 二选一）</li>
 * </ul>
 *
 * @param id         唯一标识（UUID）
 * @param name       显示名称（如 "生产环境 ES"）
 * @param type       日志源类型
 * @param properties 类型相关的连接参数（不可变）
 * @param enabled    是否启用
 * @param createdAt  创建时间
 */
public record LogSourceConfig(
        String id,
        String name,
        LogSourceType type,
        Map<String, String> properties,
        boolean enabled,
        Instant createdAt) {

    public LogSourceConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("type 不能为空");
        }
        properties = properties != null
                ? Collections.unmodifiableMap(properties)
                : Collections.emptyMap();
    }

    /** 获取指定属性的值，不存在时返回默认值 */
    public String property(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
}
