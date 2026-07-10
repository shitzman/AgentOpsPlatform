# Tasks

## V1.7 — 恢复模块边界（编排下沉）

Goal: 将 `DiagnosisService` 膨胀的编排逻辑三层下沉（runtime → domain → delivery），消除边界侵蚀技术债，并记录教训。

### agent-runtime — 通用推理循环

- [x] `agent-runtime/pom.xml` 新增 micrometer-tracing / slf4j-api / jackson-databind 依赖
- [x] `ReasoningLoop.java` — 接口：callLlm / runWithAutoToolLoop / executeToolCall(两重载) / countToolRounds
- [x] `DefaultReasoningLoop.java` — 实现：含 span + 耗时日志 + 自动工具循环
- [x] `DefaultReasoningLoopTest.java` — 8 项单测（callLlm 打标 / 无工具 / 一轮 / maxRounds / 已注册工具 / 未知工具 / 重载 / 轮次统计）

### business-exception-agent — 领域编排

- [x] `ProjectInfo.java` — record（避免领域层依赖 agent-repository）
- [x] `DiagnosisOutcome.java` — record（编排器返回 delivery 的完整产出）
- [x] `DiagnosisOrchestrator.java` — 下沉所有领域编排（诊断分支 / 多源上下文 / prompt 渲染 / 报告解析）
- [x] `DiagnosisOrchestratorTest.java` — 7 项单测（堆栈模式 / 纯日志 / JSON 失败 fallback / 无项目 / repoPath null / markdown 包裹 / traceId 透传）
- [x] 删除 `BusinessExceptionAgent.diagnose()` 死代码 + write-only `engine` 字段 + 未用 import

### agent-api — delivery 瘦身

- [x] `DiagnosisService.java` 重写为 delivery 适配层（移除 ModelClient 依赖，委托 DiagnosisOrchestrator + ReasoningLoop）
- [x] `AgentOpsConfig.java` 注册 `ReasoningLoop` + `DiagnosisOrchestrator` Bean（保留 businessExceptionAgent Bean 用于工作流注册副作用）

### 文档

- [x] `ARCHITECTURE.md` 新增 "Module Boundaries (V1.7)" 段
- [x] `DECISIONS/ADR-002-module-boundary-orchestration-sinkdown.md` — 记录边界侵蚀根因 + 三层下沉决策 + 教训
- [x] `openwiki/domain/business-exception.md` — 最小修正过时描述（L49 / 变更关注点）

### 验证

- [x] `mvn compile` 通过
- [x] `mvn test` 全量通过（原 82 项 + 新增 15 项 = 97 项，无回归）
- [x] `mvn spring-boot:run` 启动正常 + `POST /api/diagnosis` 端到端验证（链路完整跑通，返回 HTTP 200；fallback 报告因 LLM 返回非 JSON 文本，属模型/prompt 调优问题，非重构回归）

## V1.6 — 可观测流程日志

Goal: 为诊断全链路增加 SLF4J 结构化流程日志，便于开发阶段排查问题和理解执行流程。

### 日志基础设施

- [x] `agent-api/src/main/resources/logback-spring.xml` — 新建 console appender，pattern 含 `%X{traceId}`/`%X{spanId}`（Micrometer Tracing 自动注入 MDC），`com.agentops=DEBUG`，第三方框架降噪
- [x] `agent-tools/pom.xml` 新增 `slf4j-api` 依赖（版本由 spring-boot-starter-parent 管理）

### Controller 层日志（HTTP 入口）

- [x] `DiagnosisController` 加 Logger — 3 个端点（diagnose/chat/continueChat）加 INFO 入口/出口日志，catch 加 WARN 日志

### Service 层日志（业务编排核心）

- [x] `DiagnosisService` 加 Logger — 按方法加日志：
  - INFO：`diagnose` 入口/分支、`chat` 入口/出口、`continueWithTools` 入口/循环/出口、`runDiagnosisWithToolLoop` 每轮 + 结束、`callLlm` 含耗时、`finishDiagnosis` 出口
  - DEBUG：`executeStackTraceDiagnosis`、`executeLogOnlyDiagnosis`、`executeToolCall`（两重载）、`buildMultiSourceContext`/`buildMultiSourceContextForLogOnly`、`resolveToolRegistry`、`finishDiagnosis` messages 数量
  - WARN：`parseDiagnosisReport` JSON 解析失败

### 工具层日志

- [x] `InMemoryToolRegistry` 加 Logger — `register()` INFO（工具名+描述），`unregister()` DEBUG
- [x] `RouteLookupTool` 加 Logger — `getRoutes()` 首次构建 INFO（耗时+条数），`lookup()` DEBUG（path+method+命中数）
- [x] `SearchCodeTool` 加 Logger — `search()` DEBUG（pattern+命中数+耗时）

### 验证

- [x] 全量 `mvn test` 通过（agent-tools 58 项 + agent-api 5 项，无回归）
- [x] `mvn spring-boot:run` 启动正常，console 输出 7 个 `[register] tool registered:` INFO 日志
- [x] `POST /api/diagnosis` 完整链路日志可见：Controller 入口 → Service 分支 → callLlm 耗时 → 工具循环每轮 → 工具执行 DEBUG → finishDiagnosis 出口 → Controller 出口
- [x] 每行日志带 `[traceId,spanId]` 前缀，同一请求 traceId 一致

## V1.5 — 源码分析工具增强（route-lookup + search-code）

Goal: 补齐从「日志/接口路径」反向定位源码的能力，让 LLM 不必先猜对 filePath 才能读源码。

### ADR — 引入 JavaParser

- [x] `DECISIONS/ADR-001-javaparser-for-code-analysis.md` — 记录引入 javaparser-core（不含 symbol-solver）的决策、上下文、备选方案
- [x] 根 `pom.xml` 新增 `javaparser.version=3.26.4` 属性 + dependencyManagement 条目
- [x] `agent-tools/pom.xml` 新增 javaparser-core 依赖

### 新增工具 — route-lookup（接口路径反查源码）

- [x] `RouteLookupTool.java` — JavaParser 扫描 Spring Web 注解构建路由表，class-level prefix + method-level path 组合
- [x] 懒缓存（volatile + 双检锁），不监听文件变更（v1 限制）
- [x] 匹配策略：精确优先，无精确时前缀匹配；可选 method 过滤；ANY 匹配任意方法
- [x] `RouteLookupToolTest.java` — 15 项测试（构建/精确/前缀/方法过滤/无 prefix/不存在/空仓库/跳 target/定义/执行器/缓存/combinePath）

### 新增工具 — search-code（文本反查源码）

- [x] `SearchCodeTool.java` — 纯 Java NIO + Pattern 正则搜索，跳过 .git/target/build/node_modules 目录
- [x] filePattern 通配过滤（默认 *.java），maxResults 截断（上限 50），长行截断（>200 字符加 ...）
- [x] `SearchCodeToolTest.java` — 14 项测试（字面量/正则/大小写/filePattern/maxResults/跳 target/不命中/长行/无效正则/仓库不存在/定义/执行器/默认参数）

### 注册与 Prompt

- [x] `AgentOpsConfig.toolRegistry()` 注册 route-lookup + search-code（总工具数 5 → 7）
- [x] `diagnosis-system.txt` 新增「入口工具」段（route-lookup + search-code），更新工具使用策略流程（入口 → 核心 → 辅助）

### 验证

- [x] 全量 `mvn test` 通过（agent-tools 58 项，含 29 项新测试，无回归）
- [x] `GET /api/tools` 返回 7 个工具（含 route-lookup、search-code）
- [x] route-lookup 在本仓库自身能定位 ProjectController 路由
- [x] search-code 在本仓库自身能搜索 BusinessExceptionAgent 等关键词

## V1.4 — 增加代码库分析流程（read-source 工具）

Goal: 诊断时重点定位源码理解代码逻辑，git 提交定位降为辅助手段。

### 新增工具 — read-source

- [x] `SourceCodeTool.java` — 读取源文件内容（带行号），支持 startLine/endLine 范围过滤，单次最多 200 行
- [x] `SourceCodeToolTest.java` — 9 项单元测试（读取/范围/不存在/目录/超限/定义/执行器）
- [x] `AgentOpsConfig.toolRegistry()` 注册 read-source 工具
- [x] `GET /api/tools` 返回 5 个工具（含 read-source）

### Prompt 更新 — 源码优先

- [x] `diagnosis-system.txt` 工具使用策略重构：read-source 为核心工具，git-blame/git-log 为辅助
- [x] 根因分析维度更新：源码逻辑为核心线索，Git Blame 为辅助
- [x] 修复建议维度更新：引用具体代码行给出修复方向

### 验证

- [x] 全量 `mvn test` 通过（53 项，含 9 项 SourceCodeToolTest）
- [x] `GET /api/tools` 返回 read-source 工具
- [x] `/api/diagnosis` 端点正常工作（工具调用循环 + followUpQuestions）

## V1.4 — 诊断阶段主动调用工具 + 引导性追问

Goal: 首次生成诊断报告时，LLM 主动调用 git 工具分析代码并关联具体代码行，给出修复意见；信息不足时通过 followUpQuestions 引导用户补充。

### 后端 — 诊断工具调用循环

- [x] `DiagnosisReport` 新增 `followUpQuestions` 字段（List<String>，信息不足时的引导性追问）
- [x] `DiagnosisService.runDiagnosisWithToolLoop()` — 诊断阶段自动执行 LLM 请求的工具（无需用户批准）
- [x] `DiagnosisService.finishDiagnosis()` 改为调用 `runDiagnosisWithToolLoop()`（移除 `responseFormat=json_object` 以允许工具调用）
- [x] `DiagnosisService.executeToolCall(ToolCall)` 重载 — 支持 ToolCall 类型（诊断流程用）
- [x] `parseDiagnosisReport` / `fallbackReport` 更新 — 处理 `followUpQuestions` 字段
- [x] `BusinessExceptionAgent` 两处 DiagnosisReport 构造更新

### Prompts — 工具使用指引 + 引导性追问

- [x] `diagnosis-system.txt` 新增工具使用指引（git-log/git-blame/git-show/log-search）+ followUpQuestions JSON 字段
- [x] `log-analysis-system.txt` 新增 followUpQuestions JSON 字段

### 前端 — 引导性追问展示

- [x] `workbench.js` `_renderReport` — 展示 followUpQuestions（高亮区域 + 列表）
- [x] `diagnosis.js` `_renderReport` — 同步展示 followUpQuestions

### 验证

- [x] 全量 `mvn test` 通过（44 项，无回归）
- [x] `/api/diagnosis` 返回 followUpQuestions 字段（测试返回 4 条引导性追问）
- [x] 前端资源含 followUpQuestions 渲染逻辑

## V1.4 — 工具调用循环体验增强

Goal: 展示工具执行中间结果 + 循环进度可视化，提升追问中工具调用循环的用户体验。

### 后端 — 返回执行结果与轮次信息

- [x] `ToolExecutionResultVo` — 已执行工具结果 VO（id/name/arguments/result/success）
- [x] `ChatReplyVo` 扩展 — 新增 executedToolResults / toolRound / maxToolRounds 字段 + 工厂方法更新
- [x] `DiagnosisService.ChatResult` 扩展 — 新增 ExecutedTool 内部 record + executedToolResults/toolRound/maxToolRounds
- [x] `DiagnosisService.continueWithTools()` — 执行工具时收集结果，返回给前端
- [x] `DiagnosisService.chat()` — 首次 pending 时 toolRound=1
- [x] `DiagnosisController.toChatReplyVo()` — 映射新字段

### 前端 — 工具循环 UI 重新设计

- [x] `app.css` 新增工具循环样式（进度条 / 结果卡片 / 批准卡片增强）
- [x] `workbench.js` — `_handleChatResponse` 先渲染执行结果再渲染 pending/reply
- [x] `workbench.js` — `_renderToolResults` 可折叠的工具执行结果卡片
- [x] `workbench.js` — `_renderToolApproval` 增加进度条头部（第 X/8 轮 + 剩余轮次）
- [x] `diagnosis.js` — 同步改造（diag 前缀 element ID）
- [x] index.html 版本号 v1.3.0 → v1.4.0

### 验证

- [x] 全量 `mvn test` 通过（44 项，无回归）
- [x] 前端资源加载验证（workbench.js / diagnosis.js / app.css 含新代码）
- [x] `/api/chat` 返回 toolRound/maxToolRounds 字段（executedToolResults 为 null 时省略）
- [x] `/api/chat/continue` 端点正常（无效 session 返回 error）

## V1.0 — 追问工具调用循环（人在回路批准）

Goal: 追问后 LLM 可建议工具调用，用户勾选批准/修改参数后执行，结果回传 LLM 继续推理循环，直到给出最终回复。

### 后端 — 工具调用循环

- [x] `ToolCallSessionStore` — 内存态会话存储（create/get/update/remove + ToolCallSession record）
- [x] `ToolApproveRequest` DTO（sessionId + List&lt;ApprovedToolCall&gt;）+ `PendingToolCallVo` VO
- [x] `ChatReplyVo` 扩展（pendingToolCalls/sessionId 字段 + pending 工厂方法）
- [x] `DiagnosisService.chat()` 改造 — LLM 返回 tool_calls 时创建 session 返回 pendingToolCalls
- [x] `DiagnosisService.continueWithTools()` — 执行工具 + 回传 LLM + 循环（上限 8 轮）
- [x] `executeToolCall` 私有方法 — executor 查找 + 参数 JSON 解析 + 执行 + 错误兜底
- [x] `ChatResult` 扩展为 pending/reply 两种形态
- [x] `POST /api/chat/continue` 端点 + `POST /api/chat` 改造映射 pendingToolCalls

### 前端 — 工具批准 UI

- [x] `api.js` 新增 `continueChat()` 方法
- [x] `workbench.js` 追问后展示工具批准卡片（预勾选 + 可编辑参数 + 批准执行 + 循环）
- [x] `diagnosis.js` 同步改造（相同工具批准逻辑）
- [x] 多轮工具调用显示「第 N 轮」标签
- [x] index.html 版本号 v1.2.0 → v1.3.0

### 验证

- [x] 全量 `mvn test` 通过（44 项，无回归）
- [x] `/api/chat/continue` 端点已注册（无效 session 返回 error 而非 404）
- [x] 前端资源加载验证（api.js/workbench.js/diagnosis.js 含新方法）
- [x] 按端口 8088 定位停止旧进程（未杀全部 java）

## V1.0 — 项目工作台（以项目为中心的异常分析页面）

Goal: 新增项目工作台 Tab，整合项目选择 → 日志源拉取 → 工具勾选 → 异常分析 → 报告追问，作为主工作流入口。

### 后端 — 日志拉取端点

- [x] `LogFetchRequest` DTO（keyword/limit 可选）
- [x] `LogFetchResultVo` VO（success/content/message/lineCount + ok/error 工厂方法）
- [x] `LogFetchService` — 按项目查找日志源配置，分派到 `LogProvider.search()`
- [x] `POST /api/projects/{projectId}/logsources/{logSourceId}/fetch` 端点（ProjectController）

### 前端 — 工作台组件

- [x] `api.js` 新增 `fetchLogSource()` 方法
- [x] `workbench.js` 新增 WorkbenchTab 组件（两栏布局）
- [x] 左栏：项目选择 / 关联日志源复选+获取日志 / 可用工具复选 / 分析输入
- [x] 右栏：诊断报告 / 追问对话
- [x] 工具勾选与项目配置不一致时提交前同步 `PUT /api/projects/{id}/tools`
- [x] 多日志源勾选拉取，结果按来源拼接

### 集成

- [x] `index.html` 新增工作台 Tab（首位，默认 active）+ panel + script include
- [x] `app.js` 注册 WorkbenchTab.init + 默认 currentTab=workbench

### 修复 — 上传日志文件失败

- [x] `ProjectController.uploadLogSource` `@RequestPart` → `@RequestParam`
- [x] `utils.js` `showModal` 确认回调改为 async 等待

### 验证

- [x] 全量 `mvn test` 通过（44 项，无回归）
- [x] curl 验证 fetch 端点（全量拉取 + keyword 过滤）
- [x] 浏览器资源加载验证（index.html / workbench.js / app.js）

## V1.0 — 日志源增强（ES 真实集成 + 文件上传 + 测试连接）

Goal: 补齐日志源三种获取方式 — TEXT_INPUT 已可用，FILE_PATH 支持文件上传，ELASTICSEARCH 接入真实 ES 查询。

### ES 真实集成

- [x] 重写 `ElasticsearchLogProvider` — `java.net.http.HttpClient` 调用 `_search` API
- [x] 支持 Basic Auth + API Key 认证（`buildAuthHeader`）
- [x] 查询 DSL 构造（keyword/service/timeRange/limit → bool query + filter + sort）
- [x] 响应解析（`hits.hits[]._source` → 格式化日志行）
- [x] `LogProvider` 接口新增 `test()` 默认方法
- [x] `agent-tools/pom.xml` 添加 `jackson-databind` 依赖

### 测试连接端点

- [x] `LogSourceTestRequest` DTO + `LogSourceTestResultVo` VO
- [x] `LogSourceTestService` — 按 type 分派到 Provider.test()
- [x] `POST /api/logsources/test` 端点（ProjectController）

### 文件上传

- [x] `LogFileStorage` 组件 — 保存到 `./data/uploads/{uuid}.log`，路径遍历防护
- [x] `POST /api/projects/{id}/logsources/upload` 端点（multipart）
- [x] `ProjectService.uploadLogSource()` + `deleteLogSource()` 文件清理
- [x] `application.yml` 配置 multipart + upload.dir

### 前端

- [x] ES 表单增加 username/password/apiKey + 测试连接按钮
- [x] FILE_PATH 表单改为文件上传 + 可选路径输入
- [x] 日志源列表显示上传文件原始文件名
- [x] `api.js` 新增 `uploadLogSource()` + `testLogSource()` 方法

### 测试

- [x] `ElasticsearchLogProviderTest` — 15 项测试
- [x] `LogFileStorageTest` — 5 项测试
- [x] 全量 `mvn test` 通过（44 项）

## V1.0 — Controller 层 DTO/VO 重构 + Service 拆分

Goal: 消除 Controller 层的 Map 入参/出参，提取 Service 类，使 Controller 只做 HTTP 适配。

### DTO/VO 层

- [x] 新建 `com.agentops.api.dto` 包 — 8 个请求 DTO record + package-info
- [x] 新建 `com.agentops.api.vo` 包 — 12 个响应 VO record（`@JsonInclude(NON_NULL)`）+ package-info

### Service 层

- [x] `ConversationService` — 对话历史加载/保存，`StoredMessage` record 替换 Map 序列化
- [x] `DiagnosisReportPersistenceService` — 报告保存与历史分页查询
- [x] `DiagnosisService` — 诊断与追问编排（从 Controller 搬迁业务逻辑）
- [x] `ProjectService` — 项目管理 + DTO→Map 边界转换（`MySqlProjectManager` API 不变）

### Controller 重构

- [x] `DiagnosisController` 精简为 HTTP 适配层（~600 → ~135 行）
- [x] `ProjectController` 精简为 HTTP 适配层（~290 → ~215 行）
- [x] 前端 JSON 契约验证通过（health / diagnosis / chat / projects CRUD / tools / history）

## V1.0 Phase 1 — 持久化基础设施

Goal: MySQL + MyBatis-Plus 替换所有 InMemory 实现，项目配置和对话历史重启不丢失。

### 模块结构

- [x] 新建 `agent-repository` 模块（pom.xml + 目录骨架）
- [x] 父 POM 注册 `agent-repository` 子模块 + MyBatis-Plus 版本管理
- [x] `agent-api/pom.xml` 添加 `agent-repository` 依赖

### Entity 层

- [x] `ProjectEntity` — 映射 `projects` 表
- [x] `LogSourceEntity` — 映射 `log_sources` 表
- [x] `ConversationEntity` — 映射 `conversations` 表
- [x] `DiagnosisReportEntity` — 映射 `diagnosis_reports` 表（表已存在）
- [x] `MemoryEntryEntity` — 映射 `memory_entries` 表（表已存在）

### Mapper 层

- [x] `ProjectMapper` extends BaseMapper<ProjectEntity>
- [x] `LogSourceMapper` extends BaseMapper<LogSourceEntity>
- [x] `ConversationMapper` extends BaseMapper<ConversationEntity>
- [x] `DiagnosisReportMapper` extends BaseMapper<DiagnosisReportEntity>
- [x] `MemoryEntryMapper` extends BaseMapper<MemoryEntryEntity>

### 持久化实现

- [x] `MySqlMemoryStore` implements MemoryStore（基于 MemoryEntryMapper）
- [x] `MySqlProjectManager` — 从 business-exception-agent 迁入，重写为基于 Mapper

### 配置与集成

- [x] 更新 `docker/mysql/init.sql` — 新增 projects / log_sources / conversations 表
- [x] `application.yml` 新增 MySQL 数据源配置
- [x] `application-local.yml` 新增本地 H2 开发配置
- [x] `AgentOpsConfig` — DataSource + MyBatis-Plus + Bean 切换
- [ ] 更新 `ProjectController` imports → 使用 MySqlProjectManager + ProjectEntity
- [ ] 更新 `DiagnosisController` — 对话历史使用 MySqlMemoryStore
- [ ] 移除旧代码：business-exception-agent 中的 ProjectManager + Project record
- [ ] 编译验证 + 单元测试

## V0.5 剩余待办（推迟至 V2）

- [ ] 实现 Prometheus Tool（prometheus-query：按 PromQL 查询指标）
- [ ] Trace 与指标关联分析
- [ ] 诊断工作流支持跨服务调用链分析

## V1.0 Phase 2 — 数据采集增强

- [x] EnvironmentCollector — 采集目标项目运行环境
- [x] GitContextProvider — 自动检测 Git 仓库上下文
- [x] LogExtractor — 从日志文件自动提取异常堆栈
- [x] DiagnosisContext 模型增强（聚合 stackTrace + logContext + gitContext + environment）
- [x] POST /api/projects/{id}/context — 项目完整上下文快照端点

## V1.0 Phase 3 — 多源关联诊断增强

- [x] 重写诊断 System Prompt（加入 Git blame + 日志上下文 + 环境变量分析引导）
- [x] DiagnosisReport 新增 gitBlameHints / environmentFactors / logContextSummary 字段
- [x] 诊断时自动注入多源上下文（项目信息 + 运行环境 + Git Blame + 日志上下文）
- [x] POST /api/diagnosis 支持可选 logContent 字段以提取日志上下文
- [x] 诊断报告持久化到 MySQL（diagnosis_reports 表）
- [x] GET /api/diagnosis?projectId=X&page=0&size=20 — 诊断历史分页查询

## Backlog

- [ ] Redis 版 MemoryStore（会话缓存 + 短期记忆，V2）
- [ ] Patch 方案生成工作流（V3）
- [ ] Reviewer 审核步骤（AI 自审 + 人工确认，V3）
- [ ] GitHub / GitLab 集成（V3）
- [ ] 飞书 / 企业微信 / 钉钉通知插件（V2）
- [ ] MCP 集成（待有明确使用场景）
