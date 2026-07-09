/**
 * HTTP 响应 VO — Controller 层的出参载体。
 *
 * <p>均为 Java {@code record}，类级标注 {@link com.fasterxml.jackson.annotation.JsonInclude @JsonInclude(NON_NULL)}，
 * 使 null 字段被省略，匹配旧 {@code Map.of(...)} 仅包含已提供键的行为，保证前端 JSON 契约不变。
 * {@code success} 字段使用 primitive {@code boolean} 以确保始终序列化。
 */
package com.agentops.api.vo;
