package com.agentops.runtime.model;

/**
 * 模型调用异常 — 当 {@link ModelClient} 调用 LLM 失败时抛出。
 *
 * <p>覆盖以下场景：
 * <ul>
 *   <li>网络超时或连接失败</li>
 *   <li>API 返回错误（如 401 认证失败、429 限流、500 服务端错误）</li>
 *   <li>响应解析失败</li>
 * </ul>
 */
public class ModelClientException extends RuntimeException {

    private final int statusCode;

    public ModelClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public ModelClientException(int statusCode, String message) {
        super("HTTP " + statusCode + ": " + message);
        this.statusCode = statusCode;
    }

    public ModelClientException(String message) {
        super(message);
        this.statusCode = 0;
    }

    /** HTTP 状态码（网络错误等非 HTTP 场景返回 0） */
    public int getStatusCode() {
        return statusCode;
    }
}
