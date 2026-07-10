package com.agentops.tools.log;

import com.agentops.tools.core.ToolResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Elasticsearch 日志提供者 — 通过 ES {@code _search} API 执行真实日志搜索。
 *
 * <p>使用 {@link HttpClient}（Java 21 内置）发起 HTTP 请求，支持两种认证方式：
 * <ul>
 *   <li>Basic Auth — 配置 {@code username} + {@code password}</li>
 *   <li>API Key — 配置 {@code apiKey}（与 Basic Auth 二选一）</li>
 * </ul>
 *
 * <p>配置参数（来自 {@link LogSourceConfig#properties()}）：
 * <ul>
 *   <li>{@code esUrl} — ES 服务地址，如 {@code http://es-cluster:9200}</li>
 *   <li>{@code index} — 索引名，如 {@code app-logs-*}</li>
 *   <li>{@code username} — Basic Auth 用户名（可选）</li>
 *   <li>{@code password} — Basic Auth 密码（可选）</li>
 *   <li>{@code apiKey} — API Key（可选，与 Basic Auth 二选一）</li>
 * </ul>
 */
public class ElasticsearchLogProvider implements LogProvider {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ElasticsearchLogProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ========================================================================
    // 搜索
    // ========================================================================

    @Override
    public ToolResult search(Map<String, Object> args, LogSourceConfig config) {
        String esUrl = config.property("esUrl", "");
        String index = config.property("index", "");
        if (esUrl.isBlank()) {
            return ToolResult.failure("ES 地址未配置（esUrl 为空）");
        }
        if (index.isBlank()) {
            return ToolResult.failure("ES 索引未配置（index 为空）");
        }

        String requestBody = buildRequestBody(args);
        String authHeader = buildAuthHeader(config);

        try {
            HttpResponse<String> response = httpClient.send(
                    buildSearchRequest(esUrl, index, requestBody, authHeader),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return parseResponse(response.body(), args, index);
            }
            return ToolResult.failure("ES 查询失败 (HTTP " + statusCode + "): "
                    + truncate(response.body(), 500));
        } catch (ConnectException e) {
            return ToolResult.failure("无法连接 ES 服务: " + esUrl + " — " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("ES 查询被中断");
        } catch (Exception e) {
            return ToolResult.failure("ES 查询异常: " + e.getMessage());
        }
    }

    // ========================================================================
    // 测试连接
    // ========================================================================

    @Override
    public ToolResult test(LogSourceConfig config) {
        String esUrl = config.property("esUrl", "");
        if (esUrl.isBlank()) {
            return ToolResult.failure("ES 地址未配置（esUrl 为空）");
        }

        String authHeader = buildAuthHeader(config);
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(esUrl.replaceAll("/+$", "")))
                    .timeout(Duration.ofSeconds(10))
                    .GET();

            if (authHeader != null) {
                reqBuilder.header("Authorization", authHeader);
            }

            HttpResponse<String> response = httpClient.send(
                    reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                String version = extractEsVersion(response.body());
                return ToolResult.success("连接成功" + (version != null ? " (ES 版本: " + version + ")" : ""));
            }
            if (statusCode == 401 || statusCode == 403) {
                return ToolResult.failure("认证失败 (HTTP " + statusCode + ")，请检查用户名/密码或 API Key");
            }
            return ToolResult.failure("ES 连接失败 (HTTP " + statusCode + "): "
                    + truncate(response.body(), 300));
        } catch (ConnectException e) {
            return ToolResult.failure("无法连接 ES 服务: " + esUrl + " — " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("ES 连接测试被中断");
        } catch (Exception e) {
            return ToolResult.failure("ES 连接测试异常: " + e.getMessage());
        }
    }

    // ========================================================================
    // 查询 DSL 构造
    // ========================================================================

    /** 构造 ES {@code _search} 查询 DSL JSON */
    String buildRequestBody(Map<String, Object> args) {
        String keyword = (String) args.get("keyword");
        String service = (String) args.get("service");
        String timeRange = (String) args.get("timeRange");
        int limit = args.containsKey("limit")
                ? ((Number) args.get("limit")).intValue() : 10;

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode bool = objectMapper.createObjectNode();
        ObjectNode queryBool = objectMapper.createObjectNode();
        queryBool.set("bool", bool);

        addKeywordQuery(bool, keyword);
        addFilters(bool, service, timeRange);

        root.set("query", queryBool);
        root.put("size", limit);
        addSort(root);

        return root.toString();
    }

    /** keyword → query_string 查询（匹配 message 字段） */
    private void addKeywordQuery(ObjectNode bool, String keyword) {
        if (keyword == null || keyword.isBlank()) return;

        ObjectNode qsInner = objectMapper.createObjectNode();
        qsInner.put("query", keyword);
        qsInner.put("default_field", "message");

        ObjectNode queryString = objectMapper.createObjectNode();
        queryString.set("query_string", qsInner);

        bool.putArray("must").add(queryString);
    }

    /** service → term 过滤；timeRange → @timestamp range 过滤 */
    private void addFilters(ObjectNode bool, String service, String timeRange) {
        ArrayNode filterArray = bool.putArray("filter");

        if (service != null && !service.isBlank()) {
            ObjectNode serviceTerm = objectMapper.createObjectNode();
            serviceTerm.put("service", service);

            ObjectNode term = objectMapper.createObjectNode();
            term.set("term", serviceTerm);
            filterArray.add(term);
        }

        String esTimeRange = parseTimeRange(timeRange);
        if (esTimeRange != null) {
            ObjectNode timestampRange = objectMapper.createObjectNode();
            timestampRange.put("gte", esTimeRange);

            ObjectNode tsField = objectMapper.createObjectNode();
            tsField.set("@timestamp", timestampRange);

            ObjectNode range = objectMapper.createObjectNode();
            range.set("range", tsField);
            filterArray.add(range);
        }
    }

    /** 按 @timestamp 倒序排列 */
    private void addSort(ObjectNode root) {
        ObjectNode sortTs = objectMapper.createObjectNode();
        sortTs.put("@timestamp", "desc");
        root.putArray("sort").add(sortTs);
    }

    // ========================================================================
    // 认证
    // ========================================================================

    /** 构造 Authorization 头（API Key 优先，其次 Basic Auth，无认证返回 null） */
    String buildAuthHeader(LogSourceConfig config) {
        String apiKey = config.property("apiKey", "");
        if (!apiKey.isBlank()) {
            return "ApiKey " + apiKey;
        }

        String username = config.property("username", "");
        String password = config.property("password", "");
        if (!username.isBlank()) {
            String credentials = username + ":" + password;
            String encoded = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            return "Basic " + encoded;
        }

        return null;
    }

    // ========================================================================
    // 响应解析
    // ========================================================================

    /** 解析 ES 响应，提取 hits 并格式化为日志行 */
    ToolResult parseResponse(String responseBody, Map<String, Object> args, String index) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode hits = root.path("hits").path("hits");
            String keyword = (String) args.get("keyword");

            if (!hits.isArray() || hits.isEmpty()) {
                return ToolResult.success(String.format(
                        "[ELASTICSEARCH] %s (关键词: %s, 未找到匹配日志)", index, keyword));
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[ELASTICSEARCH] %s (关键词: %s, 匹配 %d 条)\n\n",
                    index, keyword, hits.size()));

            for (JsonNode hit : hits) {
                sb.append(formatLogLine(hit.path("_source"))).append("\n");
            }
            return ToolResult.success(sb.toString().trim());
        } catch (Exception e) {
            return ToolResult.failure("解析 ES 响应失败: " + e.getMessage());
        }
    }

    /** 格式化单条日志：优先提取 timestamp/level/service/message 字段 */
    private String formatLogLine(JsonNode source) {
        String timestamp = textOrNull(source, "@timestamp", "timestamp", "time");
        String level = textOrNull(source, "level", "severity");
        String service = textOrNull(source, "service", "service.name");
        String message = textOrNull(source, "message", "msg", "log");

        if (message != null) {
            return String.format("[%s] %s [%s] %s",
                    timestamp != null ? timestamp : "",
                    level != null ? level : "INFO",
                    service != null ? service : "unknown",
                    message);
        }
        return source.toString();
    }

    /** 从 JsonNode 中按多个候选字段名取第一个非空文本值 */
    private String textOrNull(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode val = node.get(name);
            if (val != null && !val.isNull() && !val.asText().isBlank()) {
                return val.asText();
            }
        }
        return null;
    }

    /** 从 ES 根端点响应中提取版本号 */
    private String extractEsVersion(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode version = root.path("version").path("number");
            return version.isTextual() ? version.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /** 将时间范围字符串（5m/1h/24h）转为 ES 时间表达式（now-5m） */
    String parseTimeRange(String timeRange) {
        if (timeRange == null || timeRange.isBlank()) return null;

        String trimmed = timeRange.trim();
        if (trimmed.startsWith("now")) return trimmed;
        if (trimmed.matches("\\d+[smhd]")) return "now-" + trimmed;

        return null;
    }

    /** 构造 _search HTTP 请求 */
    private HttpRequest buildSearchRequest(String esUrl, String index,
                                           String requestBody, String authHeader) {
        String url = esUrl.replaceAll("/+$", "") + "/" + index + "/_search";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
        return builder.build();
    }

    /** 截断字符串到指定长度 */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @Override
    public LogSourceType supportedType() {
        return LogSourceType.ELASTICSEARCH;
    }
}
