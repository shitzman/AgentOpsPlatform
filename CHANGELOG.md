# Changelog

All notable changes to AgentOps Platform will be documented in this file.

## V1.0 (in progress)

### Feature — 增加代码库分析流程：read-source 工具（v1.4.0）

#### Added — read-source 工具（源码定位核心工具）

- `SourceCodeTool` — 读取源文件内容（带行号），支持 `startLine`/`endLine` 范围过滤，单次最多 200 行。诊断时 LLM 优先使用此工具读取堆栈中项目代码帧对应的源文件，理解异常位置的代码逻辑
- `SourceCodeToolTest` — 9 项单元测试覆盖读取/范围/不存在/目录/超限/定义/执行器
- `AgentOpsConfig.toolRegistry()` 注册 read-source（总计 5 个工具：read-source + git-log + git-blame + git-show + log-search）

#### Changed — 诊断 prompt 源码优先策略

- `diagnosis-system.txt` 工具使用策略重构：`read-source` 为核心工具（定位源码），`git-blame`/`git-log`/`git-show` 为辅助（定位最近变更）
- 根因分析维度更新：源码逻辑为核心线索，Git Blame 为辅助
- 修复建议维度更新：引用具体代码行给出修复方向

### Feature — 诊断阶段主动调用工具 + 引导性追问（v1.4.0）

#### Added — 诊断工具调用循环

- `DiagnosisService.runDiagnosisWithToolLoop()` — 诊断阶段 LLM 可主动调用 git 工具（git-log/git-blame/git-show）分析堆栈中的项目代码，自动执行（无需用户批准），结果回传 LLM 继续分析
- `DiagnosisService.finishDiagnosis()` 改为使用工具调用循环，移除 `responseFormat=json_object` 限制以允许工具调用
- `DiagnosisService.executeToolCall(ToolCall)` 重载 — 支持 ToolCall 类型用于诊断流程
- 诊断 prompt（`diagnosis-system.txt`）新增工具使用指引：从堆栈提取项目代码帧 → git-blame → git-show → git-log 的分析策略

#### Added — 引导性追问

- `DiagnosisReport` 新增 `followUpQuestions` 字段（List<String>）— 信息不足时 LLM 生成引导性问题
- `log-analysis-system.txt` 同步新增 `followUpQuestions` JSON 字段
- 前端 `workbench.js` / `diagnosis.js` 在诊断报告中展示「❓ 需要更多信息」高亮区域，列出引导性追问

### Feature — 工具调用循环体验增强（v1.4.0）

#### Added — 工具执行结果可见

- `ToolExecutionResultVo` — 已执行工具结果 VO（id/name/arguments/result/success），返回给前端展示中间产物
- `ChatReplyVo` 扩展 — 新增 `executedToolResults` / `toolRound` / `maxToolRounds` 字段
- `DiagnosisService.continueWithTools()` — 执行工具时收集结果，随响应返回（此前结果只回传 LLM，用户不可见）
- `DiagnosisService.ChatResult` 新增 `ExecutedTool` 内部 record + 轮次信息

#### Added — 循环进度可视化

- `app.css` 新增工具循环样式：进度条（第 X/8 轮 + 进度条 + 剩余轮次）、可折叠的工具执行结果卡片（✅/❌ 状态 + 参数 + 输出）、增强的批准卡片
- `workbench.js` / `diagnosis.js` — `_handleChatResponse` 先渲染执行结果卡片，再渲染 pending 批准卡片或最终回复
- `_renderToolResults` — 每个工具结果为独立可折叠卡片，点击切换展开/折叠
- `_renderToolApproval` — 顶部增加进度条头部，清晰展示当前轮次与剩余额度
- index.html 版本号 v1.3.0 → v1.4.0

### Feature — 追问工具调用循环（人在回路批准）

#### Added — 工具调用循环后端

- `POST /api/chat/continue` — 批准并执行工具调用，结果回传 LLM 继续推理循环
- `ToolCallSessionStore` — 内存态会话存储，暂存工具调用循环的中间消息（assistant tool_calls + tool 结果），不写入 ConversationService（向后兼容）
- `DiagnosisService.chat()` 改造：LLM 返回 `tool_calls` 时创建 session 返回 `pendingToolCalls`，不再忽略工具调用请求
- `DiagnosisService.continueWithTools()` — 执行用户批准的工具 → 回传 LLM → 循环（上限 8 轮）
- `ToolApproveRequest` DTO + `PendingToolCallVo` VO + `ChatReplyVo` 扩展（pendingToolCalls/sessionId 字段）
- `ChatResult` 扩展为 pending/reply 两种形态

#### Added — 工具批准前端 UI

- `workbench.js` / `diagnosis.js` 追问后展示「🔧 工具调用请求」卡片：预勾选 + 可编辑参数 JSON + 批准执行
- 批准后调 `/api/chat/continue`，响应可能含新的 pendingToolCalls（循环）或最终回复
- 多轮工具调用显示「第 N 轮」标签
- `api.js` 新增 `continueChat()` 方法

### Feature — 项目工作台页面

#### Added — 日志拉取端点

- `POST /api/projects/{projectId}/logsources/{logSourceId}/fetch` — 按日志源 ID 拉取日志内容供异常分析
- `LogFetchService` — 按项目查找日志源配置，分派到 `LogProvider.search()` 执行查询（结构对称于 `LogSourceTestService`）
- `LogFetchRequest` DTO（keyword/limit 可选，默认 200 行）+ `LogFetchResultVo` VO（content/lineCount）

#### Added — 项目工作台前端组件

- 新增「🧪 项目工作台」Tab（默认首页），整合项目选择 → 日志源拉取 → 工具勾选 → 异常分析 → 报告追问
- `workbench.js` — 两栏布局：左栏（项目选择/关联日志源复选+获取日志/可用工具复选/分析输入），右栏（诊断报告/追问对话）
- 工具勾选与项目配置不一致时，提交分析前先 `PUT /api/projects/{id}/tools` 同步，保持项目级单一数据源
- 支持多日志源勾选，拉取结果按来源拼接（每段带来源头）
- `api.js` 新增 `fetchLogSource()` 方法

### Fix — 上传日志文件失败

- `ProjectController.uploadLogSource` 将 `@RequestPart` 改为 `@RequestParam`
- 根因：浏览器 FormData 不为文本 part 设置 Content-Type 头，`@RequestPart` 需 Content-Type 协商而失败；`@RequestParam` 不需要
- `utils.js` `showModal` 的确认回调改为 async，`await onConfirm()` 后再关闭模态框（修复异步操作未完成即关闭）

### Feature — 日志源增强（ES 真实集成 + 文件上传 + 测试连接）

#### Changed — ElasticsearchLogProvider 重写

- 从模拟实现改为通过 `java.net.http.HttpClient` 调用 ES `_search` API 的真实查询
- 支持 Basic Auth（`username` + `password`）和 API Key（`apiKey`）两种认证方式
- 查询 DSL 从 LLM 工具参数映射：keyword → `query_string`、service → `term` 过滤、timeRange → `@timestamp` range、limit → `size`
- 响应解析提取 `hits.hits[]._source`，格式化为 `[timestamp] level [service] message` 日志行
- HttpClient 单例复用，连接超时 5s、请求超时 30s
- `LogProvider` 接口新增 `test(LogSourceConfig)` 默认方法用于连通性测试

#### Added — 测试连接端点

- `POST /api/logsources/test` — 保存前验证日志源配置是否可用
- `LogSourceTestService` — 按 type 分派到对应 Provider 的 `test()` 方法
- ES 测试：`GET {esUrl}` 返回版本号；FILE_PATH 测试：校验文件存在可读；TEXT_INPUT 测试：校验 rawText 非空
- `LogSourceTestRequest` DTO + `LogSourceTestResultVo` VO

#### Added — 文件上传

- `POST /api/projects/{id}/logsources/upload` — multipart 文件上传，保存为 FILE_PATH 日志源
- `LogFileStorage` — 文件保存到 `./data/uploads/{uuid}.log`，含路径遍历防护
- `ProjectService.uploadLogSource()` — 保存文件并创建日志源（properties 含 `filePath` + `originalFileName`）
- `ProjectService.deleteLogSource()` — 删除时自动清理上传文件（通过 `originalFileName` 属性区分上传文件 vs 手动路径）
- `application.yml` 新增 `spring.servlet.multipart` 和 `agentops.upload.dir` 配置

#### Changed — 前端日志源管理

- ES 表单增加 username/password/apiKey 输入框和「测试连接」按钮
- FILE_PATH 表单改为文件上传（file input）+ 可选服务器路径输入
- 日志源列表显示上传文件的原始文件名

#### Added — 单元测试

- `ElasticsearchLogProviderTest` — 15 项测试（DSL 构造、认证头、时间范围解析、响应解析）
- `LogFileStorageTest` — 5 项测试（文件保存、删除、空文件校验、路径安全）

### Refactor — Controller 层 DTO/VO + Service 拆分

#### Changed — 代码风格优化

- `DiagnosisController`（~600 → ~135 行）和 `ProjectController`（~290 → ~215 行）改为纯 HTTP 适配层
- 所有 `Map<String,String>`/`Map<String,Object>` 入参替换为请求 DTO record
- 所有 `Map<String,Object>` 出参替换为响应 VO record（类级 `@JsonInclude(NON_NULL)`，保持前端 JSON 契约不变）
- 长方法拆分为短方法并补充 Javadoc

#### Added — Service 层

- `DiagnosisService` — 诊断与追问编排（堆栈解析、多源上下文、LLM 调用、报告解析）
- `ConversationService` — 对话历史加载与持久化，使用 `StoredMessage` record 替换 Map 序列化
- `DiagnosisReportPersistenceService` — 诊断报告保存与历史分页查询
- `ProjectService` — 项目/日志源/工具管理，DTO→Map 边界转换（`MySqlProjectManager` API 不变）

#### Added — DTO/VO 包

- `com.agentops.api.dto` — 8 个请求 DTO record（`DiagnosisRequest`、`ChatFollowUpRequest`、`ProjectCreateRequest` 等）
- `com.agentops.api.vo` — 12 个响应 VO record（`DiagnosisResponseVo`、`ChatReplyVo`、`HealthVo`、`SimpleResultVo` 等）

### Phase 3 — 多源关联诊断增强

#### Changed — 诊断模型增强

- `DiagnosisReport` 新增 3 个字段（V1.0 Phase 3）：
  - `gitBlameHints: List<String>` — 可疑的 Git 提交线索
  - `environmentFactors: List<String>` — 可能相关的环境因素
  - `logContextSummary: String` — 关联日志上下文的关键发现摘要

#### Changed — System Prompt 重写

- `diagnosis-system.txt`：新增 3 个诊断维度（Git Blame 线索 / 环境因素 / 日志上下文摘要）
- JSON Schema 输出格式新增对应的 3 个字段
- 引导 LLM 利用 Git Blame、环境信息、日志上下文进行交叉验证

#### Added — 多源上下文注入

- `POST /api/diagnosis` 诊断时自动注入多源上下文：
  - 项目信息（名称、描述）
  - 运行环境（Java 版本、OS、JVM 参数、内存）
  - Git 上下文（分支、最近提交、项目代码帧的 Git Blame）
  - 日志上下文（从可选 `logContent` 字段提取的关联日志行）
- 新增 `DiagnosisController.buildMultiSourceContext()` 私有方法进行上下文收集

#### Added — 诊断持久化

- 每次诊断自动将报告保存到 `diagnosis_reports` 表（MySQL/H2）
- `GET /api/diagnosis?projectId=X&page=0&size=20` — 诊断历史分页查询
- `DiagnosisController` 新增 `DiagnosisReportMapper` 依赖

### Phase 2 — 数据采集增强

#### Added — 模型层

- `EnvironmentInfo`（agent-tools）：JVM / OS / 内存 / CPU 环境信息记录
- `GitContext`（agent-tools）：Git 仓库状态快照（分支 + 提交 + Blame + 未提交变更）
  - 内嵌 `CommitInfo` 和 `BlameInfo` 子记录
- `DiagnosisContext`（business-exception-agent）：多源数据聚合上下文
  - 聚合：stackTrace + logContext + gitContext + environment + projectInfo
  - `toPromptText()` 方法生成 Markdown 格式的 LLM Prompt 注入文本

#### Added — 采集器

- `EnvironmentCollector`（agent-tools）：基于 ManagementFactory 采集 JVM/OS 运行环境
- `GitContextProvider`（agent-tools）：基于 git 命令收集分支/提交/Blame/变更状态
  - 内嵌 `FileLine` 记录，避免跨模块类型依赖
- `LogExtractor`（agent-tools）：从原始日志文本自动提取异常堆栈和上下文行
  - 支持 Java 异常格式识别（Exception/Error/Throwable）
  - 支持 Caused by 链式异常
  - 支持提取异常前后 N 行的日志上下文

#### Added — API

- `POST /api/projects/{id}/context`：项目完整上下文快照端点
  - 入参：`{ "logContent": "(可选) 原始日志文本" }`
  - 出参：DiagnosisContext JSON（含环境 + Git + 提取的堆栈和日志上下文）

### Phase 1 — 持久化基础设施

#### Added — 新模块

- 新建 `agent-repository` 模块：统一的 MySQL 数据持久化层
  - MyBatis-Plus 3.5.10.1 + MySQL Connector J
  - Entity 层：ProjectEntity / LogSourceEntity / ConversationEntity / DiagnosisReportEntity / MemoryEntryEntity
  - Mapper 层：全部继承 BaseMapper<T>，开箱即用 CRUD
  - MySqlMemoryStore：MemoryStore 的 MySQL 实现（替换 InMemoryMemoryStore）
  - MySqlProjectManager：Project/LogSource CRUD 服务（基于 Mapper，替换旧版 JSON 序列化方案）

#### Changed — 模块调整

- `business-exception-agent`：移除 ProjectManager 和 Project record（迁入 agent-repository）
- `agent-api`：新增 agent-repository 依赖，DataSource + MyBatis-Plus 自动配置
- `application.yml`：新增 MySQL 数据源配置，开发环境保留 H2 选项
- `docker/mysql/init.sql`：新增 projects / log_sources / conversations 表

#### Architecture

- 新建 `agent-repository` 模块负责所有数据持久化
- Entity 类使用 MyBatis-Plus 注解，Mapper 继承 BaseMapper
- MySqlMemoryStore 实现 MemoryStore 接口，可无缝替换 InMemory 版本
- 开发 profile 使用 H2 内存数据库，生产 profile 使用 MySQL

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

## V0.1 (Initial)

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
