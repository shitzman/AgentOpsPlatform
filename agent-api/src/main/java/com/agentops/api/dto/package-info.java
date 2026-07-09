/**
 * HTTP 请求 DTO — 封装 Controller 层 {@code @RequestBody} 的入参载体。
 *
 * <p>均为 Java {@code record}，匹配平台既有数据载体风格。必填字段校验在 Service 层完成，
 * 不在 record 构造器中抛异常，以保证校验失败时返回统一的 {@code {success:false,error:"..."}} 格式。
 */
package com.agentops.api.dto;
