# Changelog

All notable changes to AgentOps Platform will be documented in this file.

## Unreleased

### Added

- Added project bootstrap prompt for first-time AI agent onboarding.
- Added continue-development prompt for task-by-task implementation.
- Added architecture, review, bugfix, refactor, and release prompt assets.
- Added repository-level AI collaboration instructions in `AGENTS.md` and `CLAUDE.md`.
- Added initial roadmap, architecture, and task tracking documents.
- Added the initial Maven multi-module skeleton with root aggregator POM and module POMs.
- Added Java 21 and Spring Boot 3.5.7 parent build configuration.
- Added standard Maven source, resource, and test placeholder structure for all platform modules.
- Added Tool Registry interfaces: `ToolDefinition`, `ToolResult`, `ToolExecutor`, and `ToolRegistry`.
- Added Prompt Registry interfaces: `PromptTemplate` with `{{变量名}}` placeholder rendering, and `PromptRegistry`.
- Added lightweight Workflow interfaces: `WorkflowContext`, `WorkflowStep`, `WorkflowStepException`, `WorkflowDefinition`, and `WorkflowEngine`.
- Added Memory interfaces: `MemoryEntry` and `MemoryStore` with CRUD and search operations.
- Added OpenAI Java SDK integration boundary: `ChatMessage`, `ToolCall`, `ChatRequest`, `ChatResponse`, `ModelClient`, and `ModelClientException` in `agent-runtime`.
- Added Docker Compose baseline with MySQL 8.0, Redis 7, Prometheus, Grafana, and OpenTelemetry Collector.
- Removed Kafka from infrastructure (defer to later milestone if needed).
- Switched from PostgreSQL to MySQL for better enterprise compatibility.

## V0.3

### Added

- JSON Mode 结构化输出：`ChatRequest` 新增 `responseFormat`，`OpenAIModelClient` 支持 `response_format: json_object`
- 增强 `DiagnosisReport`：新增 `severity`（严重级别）、`impactScope`（影响范围）、`urgency`（紧急程度）
- 重写诊断 System Prompt，内嵌 JSON Schema，新增诊断维度
- `DiagnosisController` 支持解析 LLM JSON 响应为结构化 `DiagnosisReport`（含 markdown 代码块清洗 + 降级）
- 4 类单元测试共 20 个用例：`PromptTemplateTest` / `InMemoryToolRegistryTest` / `SequentialWorkflowEngineTest` / `InMemoryMemoryStoreTest`
- 集成 SpringDoc OpenAPI（Swagger UI 访问 `/swagger-ui.html`）

### Added

- 实现 `SimpleWorkflowContext` — 基于 LinkedHashMap 的 Workflow 上下文
- 实现 `SequentialWorkflowEngine` — 顺序 Workflow 执行引擎，遇错即停
- 实现 `InMemoryToolRegistry` — 线程安全的工具注册表
- 实现 `InMemoryPromptRegistry` — 支持 classpath 自动加载 prompt 文件
- 实现 `InMemoryMemoryStore` — 内存 Memory 存储（支持自动 ID 生成）
- 实现 `OpenAIModelClient` — 基于 HttpClient + Jackson，兼容 OpenAI/DeepSeek/通义千问等
- 搭建 Spring Boot API 层：`AgentOpsApplication` + `AgentOpsConfig` + `DiagnosisController`
- 添加 `application.yml` 配置，支持通过环境变量 `AGENTOPS_LLM_*` 切换 LLM 服务商
- 默认 LLM 使用 DeepSeek（国内可直接访问）
- Added Business Exception Agent with `StackTrace`, `StackTraceFrame`, `DiagnosisReport` models, 3-step diagnosis workflow, and diagnosis system prompt template.

