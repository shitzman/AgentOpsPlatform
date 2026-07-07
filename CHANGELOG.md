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

## V0.5

### Added — 可观测性

- OpenTelemetry 集成：添加 `spring-boot-starter-actuator` + `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` 依赖
- 链路追踪自动导出至 OpenTelemetry Collector（OTLP HTTP `localhost:4318`）
- 应用配置：`management.tracing.sampling.probability=1.0`（开发环境全量采样）
- `DiagnosisReport` 新增 `traceId` 字段，支持关联分布式追踪数据
- `DiagnosisController` 为 `/api/diagnosis` 和 `/api/chat` 端点创建 Span（含 LLM 调用子 Span）
- API 响应中返回 `traceId`，方便在 OTel 后端（Jaeger/Tempo）中检索对应请求

### Added — 项目配置管理系统

**后端（10 个新文件）：**
- `Project` + `LogSourceConfig` + `LogSourceType` + `LogProvider` 模型层（agent-tools / business-exception-agent）
- `TextInputLogProvider` / `FileLogProvider` / `ElasticsearchLogProvider` 三种日志源实现
- `LogProviderRegistry` 接口 + `InMemoryLogProviderRegistry` 实现
- `FilteredToolRegistry` — 项目级工具过滤装饰器
- `ProjectManager` — 核心服务：Project/LogSource CRUD + `buildProjectToolRegistry(projectId)` 为项目构建专属工具集
- `ProjectController` — REST API（11 个端点：项目、日志源、工具 CRUD）
- `LogTool` 重构为可插拔（支持绑定 LogProvider + LogSourceConfig）
- `DiagnosisController` 支持可选 `projectId` → 项目级工具集 + 项目上下文注入

**前端（7 个文件，替代旧 index.html）：**
- `css/app.css` — 全局样式（CSS 变量、卡片、Modal、Tool Tag、Badge 等）
- `js/app.js` — 应用入口（AppState + EventBus + TabManager）
- `js/api.js` — HTTP 客户端（所有 fetch 请求单一出口）
- `js/utils.js` — DOM 工具（notify、showModal、badge、escapeHtml 等）
- `js/components/diagnosis.js` — 诊断测试选项卡（堆栈输入 + 项目选择 + 报告渲染 + 追问聊天）
- `js/components/projects.js` — 项目配置选项卡（卡片列表 + 新建/编辑 Modal + 工具勾选框）
- `js/components/logsources.js` — 日志源管理选项卡（类型感知表单：TEXT_INPUT/FILE_PATH/ELASTICSEARCH）

**架构特性：**
- 事件驱动（EventBus）解耦 3 个选项卡组件
- AppState 全局状态管理（无框架依赖）
- Modal 弹窗复用（项目表单、日志源表单、删除确认）
- CSS 变量主题管理

## V0.3

### Added

- JSON Mode 结构化输出：`ChatRequest` 新增 `responseFormat`，`OpenAIModelClient` 支持 `response_format: json_object`
- 增强 `DiagnosisReport`：新增 `severity`（严重级别）、`impactScope`（影响范围）、`urgency`（紧急程度）
- 重写诊断 System Prompt，内嵌 JSON Schema，新增诊断维度
- `DiagnosisController` 支持解析 LLM JSON 响应为结构化 `DiagnosisReport`（含 markdown 代码块清洗 + 降级）
- 4 类单元测试共 20 个用例：`PromptTemplateTest` / `InMemoryToolRegistryTest` / `SequentialWorkflowEngineTest` / `InMemoryMemoryStoreTest`
- 集成 SpringDoc OpenAPI（Swagger UI 访问 `/swagger-ui.html`）

## V0.4

### Added

- Git Tool：`git-log`（查看文件提交历史）、`git-blame`（查看行的修改者）、`git-show`（查看 commit 详情）
- Log Tool：`log-search`（按关键词/服务/时间范围搜索日志，V0.4 模拟实现）
- 多轮对话：`POST /api/chat` 追问端点，支持 `conversationId` 关联历史对话
- MemoryStore 持久化对话历史（conversation:* 类型）
- 启动时自动注册 4 个工具到 ToolRegistry
- ChatRequest 传入工具定义列表，LLM 可选择调用工具

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

