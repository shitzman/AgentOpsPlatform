package com.agentops.runtime;

import com.agentops.runtime.model.*;
import com.agentops.tools.core.ToolDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAI 兼容的 ModelClient 实现 — 通过 REST API 调用 LLM。
 *
 * <p>兼容所有 OpenAI API 格式的模型服务：
 * <ul>
 *   <li>OpenAI GPT-4o / GPT-4o-mini</li>
 *   <li>DeepSeek V3 / R1（国内常用）</li>
 *   <li>通义千问（阿里云）</li>
 *   <li>其他兼容服务</li>
 * </ul>
 *
 * <p>通过配置 baseUrl 和 apiKey 切换不同的模型服务商。
 */
public class OpenAIModelClient implements ModelClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;

    /**
     * @param baseUrl      API 地址（如 https://api.openai.com/v1 或 https://api.deepseek.com/v1）
     * @param apiKey       API 密钥
     * @param defaultModel 默认模型名（如 gpt-4o / deepseek-chat）
     */
    public OpenAIModelClient(String baseUrl, String apiKey, String defaultModel) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            Map<String, Object> requestBody = buildRequestBody(request);
            String json = objectMapper.writeValueAsString(requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ModelClientException(response.statusCode(),
                        "LLM API 返回错误: " + response.body());
            }

            return parseResponse(response.body());
        } catch (ModelClientException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelClientException("请求被中断", e);
        } catch (Exception e) {
            throw new ModelClientException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 OpenAI 兼容的请求体 JSON。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model() != null ? request.model() : defaultModel);
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());

        // JSON Mode：要求 LLM 返回严格 JSON
        if (request.isJsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        // 转换消息列表
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage msg : request.messages()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.role());
            if (msg.content() != null) {
                m.put("content", msg.content());
            }
            if (msg.toolCallId() != null) {
                m.put("tool_call_id", msg.toolCallId());
            }
            if (!msg.toolCalls().isEmpty()) {
                List<Map<String, Object>> toolCalls = msg.toolCalls().stream()
                        .map(tc -> {
                            Map<String, Object> tcMap = new LinkedHashMap<>();
                            tcMap.put("id", tc.id());
                            tcMap.put("type", "function");
                            Map<String, Object> func = new LinkedHashMap<>();
                            func.put("name", tc.name());
                            func.put("arguments", tc.arguments());
                            tcMap.put("function", func);
                            return tcMap;
                        })
                        .collect(Collectors.toList());
                m.put("tool_calls", toolCalls);
            }
            messages.add(m);
        }
        body.put("messages", messages);

        // 转换工具定义
        if (!request.tools().isEmpty()) {
            List<Map<String, Object>> tools = request.tools().stream()
                    .map(td -> {
                        Map<String, Object> tool = new LinkedHashMap<>();
                        tool.put("type", "function");
                        Map<String, Object> func = new LinkedHashMap<>();
                        func.put("name", td.name());
                        func.put("description", td.description());
                        func.put("parameters", td.parameters());
                        tool.put("function", func);
                        return tool;
                    })
                    .collect(Collectors.toList());
            body.put("tools", tools);
        }

        return body;
    }

    /**
     * 解析 OpenAI 兼容的响应 JSON。
     */
    @SuppressWarnings("unchecked")
    private ChatResponse parseResponse(String responseBody) {
        try {
            Map<String, Object> root = objectMapper.readValue(responseBody, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new ModelClientException("LLM 返回结果中没有 choices");
            }

            Map<String, Object> choice = choices.get(0);
            String finishReason = (String) choice.get("finish_reason");
            Map<String, Object> message = (Map<String, Object>) choice.get("message");

            String content = (String) message.get("content");

            // 解析工具调用
            List<ToolCall> toolCalls = Collections.emptyList();
            List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (rawToolCalls != null && !rawToolCalls.isEmpty()) {
                toolCalls = rawToolCalls.stream()
                        .map(tc -> {
                            String id = (String) tc.get("id");
                            Map<String, Object> func = (Map<String, Object>) tc.get("function");
                            String name = (String) func.get("name");
                            String arguments = (String) func.get("arguments");
                            return new ToolCall(id, name, arguments);
                        })
                        .collect(Collectors.toList());
            }

            return new ChatResponse(content, toolCalls, finishReason);
        } catch (JsonProcessingException | ClassCastException e) {
            throw new ModelClientException("解析 LLM 响应失败: " + e.getMessage(), e);
        }
    }
}
