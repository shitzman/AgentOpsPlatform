package com.agentops.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchLogProvider 单元测试 — 验证查询 DSL 构造、认证头、时间范围解析和响应解析。
 *
 * <p>不发起真实 HTTP 请求，只测试纯逻辑方法。
 */
class ElasticsearchLogProviderTest {

    private ElasticsearchLogProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        provider = new ElasticsearchLogProvider();
    }

    // ========================================================================
    // buildRequestBody
    // ========================================================================

    @Test
    @DisplayName("仅 keyword 时构造 query_string 查询")
    void buildRequestBodyKeywordOnly() throws Exception {
        String json = provider.buildRequestBody(Map.of("keyword", "NullPointerException"));

        JsonNode root = objectMapper.readTree(json);
        assertEquals("NullPointerException",
                root.path("query").path("bool").path("must").get(0)
                        .path("query_string").path("query").asText());
        assertEquals("message",
                root.path("query").path("bool").path("must").get(0)
                        .path("query_string").path("default_field").asText());
        assertEquals(10, root.path("size").asInt()); // 默认 limit
    }

    @Test
    @DisplayName("keyword + service 添加 term 过滤")
    void buildRequestBodyWithService() throws Exception {
        String json = provider.buildRequestBody(Map.of(
                "keyword", "ERROR", "service", "order-service"));

        JsonNode root = objectMapper.readTree(json);
        JsonNode filters = root.path("query").path("bool").path("filter");
        assertEquals("order-service",
                filters.get(0).path("term").path("service").asText());
    }

    @Test
    @DisplayName("keyword + timeRange 添加 @timestamp range 过滤")
    void buildRequestBodyWithTimeRange() throws Exception {
        String json = provider.buildRequestBody(Map.of(
                "keyword", "ERROR", "timeRange", "5m"));

        JsonNode root = objectMapper.readTree(json);
        JsonNode filters = root.path("query").path("bool").path("filter");
        assertEquals("now-5m",
                filters.get(0).path("range").path("@timestamp").path("gte").asText());
    }

    @Test
    @DisplayName("limit 映射到 size 字段")
    void buildRequestBodyWithLimit() throws Exception {
        String json = provider.buildRequestBody(Map.of(
                "keyword", "test", "limit", 50));

        JsonNode root = objectMapper.readTree(json);
        assertEquals(50, root.path("size").asInt());
    }

    @Test
    @DisplayName("查询包含 @timestamp desc 排序")
    void buildRequestBodyHasSort() throws Exception {
        String json = provider.buildRequestBody(Map.of("keyword", "test"));

        JsonNode root = objectMapper.readTree(json);
        assertEquals("desc", root.path("sort").get(0).path("@timestamp").asText());
    }

    // ========================================================================
    // buildAuthHeader
    // ========================================================================

    @Test
    @DisplayName("API Key 优先于 Basic Auth")
    void buildAuthHeaderApiKeyPriority() {
        Map<String, String> props = Map.of(
                "apiKey", "my-api-key",
                "username", "elastic",
                "password", "changeme");
        LogSourceConfig config = createConfig(props);

        assertEquals("ApiKey my-api-key", provider.buildAuthHeader(config));
    }

    @Test
    @DisplayName("Basic Auth 编码正确")
    void buildAuthHeaderBasicAuth() {
        Map<String, String> props = Map.of("username", "elastic", "password", "changeme");
        LogSourceConfig config = createConfig(props);

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("elastic:changeme".getBytes());
        assertEquals(expected, provider.buildAuthHeader(config));
    }

    @Test
    @DisplayName("无认证时返回 null")
    void buildAuthHeaderNoAuth() {
        LogSourceConfig config = createConfig(Map.of("esUrl", "http://es:9200"));
        assertNull(provider.buildAuthHeader(config));
    }

    // ========================================================================
    // parseTimeRange
    // ========================================================================

    @Test
    @DisplayName("5m → now-5m")
    void parseTimeRangeMinutes() {
        assertEquals("now-5m", provider.parseTimeRange("5m"));
    }

    @Test
    @DisplayName("1h → now-1h")
    void parseTimeRangeHours() {
        assertEquals("now-1h", provider.parseTimeRange("1h"));
    }

    @Test
    @DisplayName("now-1d 直接透传")
    void parseTimeRangePassthrough() {
        assertEquals("now-1d", provider.parseTimeRange("now-1d"));
    }

    @Test
    @DisplayName("null/blank/无效 → null")
    void parseTimeRangeInvalid() {
        assertNull(provider.parseTimeRange(null));
        assertNull(provider.parseTimeRange(""));
        assertNull(provider.parseTimeRange("invalid"));
    }

    // ========================================================================
    // parseResponse
    // ========================================================================

    @Test
    @DisplayName("解析含 hits 的 ES 响应")
    void parseResponseWithHits() {
        String esResponse = """
                {"hits": {"hits": [
                  {"_source": {"@timestamp": "2026-07-08T10:00:00Z", "level": "ERROR",
                   "service": "order-svc", "message": "NullPointerException at line 42"}},
                  {"_source": {"@timestamp": "2026-07-08T10:01:00Z", "level": "WARN",
                   "service": "order-svc", "message": "请求超时"}}
                ]}}""";

        ToolResult result = provider.parseResponse(esResponse,
                Map.of("keyword", "NullPointer"), "app-logs-*");

        assertTrue(result.success());
        assertTrue(result.output().contains("匹配 2 条"));
        assertTrue(result.output().contains("NullPointerException at line 42"));
        assertTrue(result.output().contains("请求超时"));
    }

    @Test
    @DisplayName("空 hits 返回未找到匹配")
    void parseResponseEmptyHits() {
        String esResponse = """
                {"hits": {"hits": []}}""";

        ToolResult result = provider.parseResponse(esResponse,
                Map.of("keyword", "nonexistent"), "app-logs-*");

        assertTrue(result.success());
        assertTrue(result.output().contains("未找到匹配日志"));
    }

    @Test
    @DisplayName("无效 JSON 返回失败")
    void parseResponseInvalidJson() {
        ToolResult result = provider.parseResponse("not json",
                Map.of("keyword", "test"), "app-logs-*");

        assertFalse(result.success());
        assertTrue(result.error().contains("解析 ES 响应失败"));
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private LogSourceConfig createConfig(Map<String, String> properties) {
        return new LogSourceConfig("test-id", "test", LogSourceType.ELASTICSEARCH,
                properties, true, java.time.Instant.now());
    }
}
