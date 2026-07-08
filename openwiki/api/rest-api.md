# REST API 与 Web 控制台

AgentOps Platform 通过 Spring Boot（端口 8088）暴露 REST API，同时提供一个基于浏览器的 SPA Web 控制台。所有端点均在 `/agent-api/src/main/java/com/agentops/api/controller/` 中定义。

## REST API 端点

### 诊断端点（`DiagnosisController`）

**源文件**：`/agent-api/src/main/java/com/agentops/api/controller/DiagnosisController.java`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/diagnosis` | 提交 Java 堆栈，执行 LLM 诊断 |
| `POST` | `/api/chat` | 多轮追问（需 `conversationId`） |
| `GET` | `/api/health` | 健康检查 |

#### `POST /api/diagnosis`

提交一个 Java 异常堆栈进行诊断。这是核心诊断入口。

**请求体**：
```json
{
  "stackTrace": "java.lang.NullPointerException: Cannot invoke...\n\tat com.example.Service.method(Service.java:42)",
  "projectId": "optional-project-uuid"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `stackTrace` | String | ✅ | 原始 Java 堆栈文本 |
| `projectId` | String | 否 | 关联项目 ID，指定后使用项目专属工具集 |

**处理流程**：
1. 通过 `BusinessExceptionAgent` 解析堆栈（结构化异常类型、消息、帧）
2. 如果有 `projectId`，通过 `ProjectManager.buildProjectToolRegistry()` 构建项目专属工具集
3. 渲染 `diagnosis-system` Prompt 模板（注入异常上下文）
4. 如果提供 `conversationId`，从 `MemoryStore` 加载对话历史
5. 调用 LLM（附带工具定义 + System Prompt）
6. 解析 JSON 响应为 `DiagnosisReport`
7. 保存对话到 `MemoryStore`（type: `conversation:*`）
8. 所有步骤包裹 OpenTelemetry Span

**响应**：`DiagnosisReport` JSON 对象（含 `traceId` 字段用于 OTel 追踪检索）。

#### `POST /api/chat`

多轮追问端点，在已有诊断基础上继续对话。

**请求体**：
```json
{
  "message": "这个异常和最近的代码变更有关吗？",
  "conversationId": "conv-uuid-from-diagnosis",
  "projectId": "optional"
}
```

**关键逻辑**：
- 从 `MemoryStore` 加载 `conversationId` 的历史消息
- 注入 System Prompt（保证追问场景下的角色一致性）
- 持久化完整对话历史到 `MemoryStore`

> **修复历史**：commit `57b066f` 修复了追问端点注入 System Prompt 的问题；commit `42c18a3` 修复了多轮对话 `conversationId` 丢失问题。

#### `GET /api/health`

```json
{
  "status": "UP",
  "version": "0.5.0-SNAPSHOT",
  "prompts": 1,
  "tools": 4
}
```

### 项目配置端点（`ProjectController`）

**源文件**：`/agent-api/src/main/java/com/agentops/api/controller/ProjectController.java`（V0.5 新增）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/projects` | 创建项目 |
| `GET` | `/api/projects` | 列出所有项目 |
| `GET` | `/api/projects/{id}` | 获取项目详情（含日志源） |
| `PUT` | `/api/projects/{id}` | 更新项目 |
| `DELETE` | `/api/projects/{id}` | 删除项目（级联删除日志源） |
| `GET` | `/api/tools` | 列出全局可用工具名称 |
| `PUT` | `/api/projects/{id}/tools` | 设置项目启用的工具 |
| `GET` | `/api/projects/{id}/logsources` | 列出项目的日志源 |
| `POST` | `/api/projects/{id}/logsources` | 添加日志源 |
| `PUT` | `/api/projects/{id}/logsources/{lsId}` | 更新日志源 |
| `DELETE` | `/api/projects/{id}/logsources/{lsId}` | 删除日志源 |

**项目创建/更新请求体**：
```json
{
  "name": "订单服务",
  "description": "电商订单核心服务",
  "gitRepoUrl": "https://gitee.com/example/order-service.git",
  "gitRepoLocalPath": "/home/projects/order-service"
}
```

**日志源添加请求体**：
```json
{
  "name": "生产日志",
  "type": "ELASTICSEARCH",
  "properties": {
    "esUrl": "http://es-cluster:9200",
    "index": "app-logs-*"
  },
  "enabled": true
}
```

日志源类型（`LogSourceType` 枚举）：`TEXT_INPUT`（文本输入）、`FILE_PATH`（本地文件）、`ELASTICSEARCH`（ES 集群搜索）。

## Web 控制台

**位置**：`/agent-api/src/main/resources/static/`

V0.5 新增的多文件模块化 SPA，纯原生 JavaScript（无框架依赖，无构建工具）。替代了旧的单文件 `index.html`。

### 文件结构（7 个文件）

| 文件 | 说明 |
|------|------|
| `index.html` | SPA 外壳：头部（logo + 版本 + 健康状态点）、选项卡导航、3 个面板容器 |
| `css/app.css` | 全局 CSS 设计系统：CSS 变量主题、卡片、Modal、Tool Tag、Badge |
| `js/app.js` | 应用入口：`AppState`（全局状态）、`EventBus`（发布/订阅）、`TabManager`（选项卡切换）、健康轮询 |
| `js/api.js` | HTTP 客户端：所有 `fetch()` 调用单一出口，统一错误处理 |
| `js/utils.js` | DOM 工具：`notify()`、`showModal()`、`badge()`、`escapeHtml()` 等 |
| `js/components/diagnosis.js` | 诊断测试选项卡：堆栈输入 + 项目选择器 + 报告渲染 + 追问聊天 |
| `js/components/projects.js` | 项目配置选项卡：卡片列表 + 新建/编辑 Modal + 工具勾选框 |
| `js/components/logsources.js` | 日志源管理选项卡：类型感知表单（TEXT_INPUT / FILE_PATH / ELASTICSEARCH） |

### 架构设计

**事件驱动（EventBus）**：3 个选项卡组件通过发布/订阅解耦：

```
EventBus
  ├── 'projects-changed'  →  诊断选项卡更新项目下拉列表
  ├── 'project-selected'  →  日志源选项卡加载对应项目
  └── 'tab-switched'      →  组件按需刷新
```

**全局状态（AppState）**：
```javascript
AppState = {
  currentTab: 'diagnosis',     // 当前激活选项卡
  selectedProjectId: null,    // 当前选中项目
  projects: [],               // 项目列表
  tools: []                   // 可用工具列表
}
```

**协议**：纯 `fetch()` REST 调用，所有请求通过 `Api` 对象单出口发出。健康检查 30 秒轮询一次 `/api/health`，在头部显示绿色/红色状态点。

### 3 个选项卡

| 选项卡 | 功能 |
|------|------|
| **诊断测试** | 粘贴堆栈 → 选择项目（可选）→ 提交诊断 → 渲染结构化报告 → 追问聊天 |
| **项目配置** | 卡片式项目列表 → 新建/编辑 Modal → 勾选启用的工具（Git/Log） |
| **日志源管理** | 按项目查看/添加日志源 → 类型感知表单（TEXT_INPUT 显示文本框，FILE_PATH 显示路径，ELASTICSEARCH 显示 URL + index） |

## Spring Bean 装配

**源文件**：`/agent-api/src/main/java/com/agentops/api/config/AgentOpsConfig.java`

`AgentOpsConfig` 是应用的**组合根**，通过 `@Configuration` + `@Bean` 装配所有实例：

| Bean | 实现 | 注册内容 |
|------|------|------|
| `ToolRegistry` | `InMemoryToolRegistry` | Git（log/blame/show）+ Log + 通用工具 |
| `MemoryStore` | `InMemoryMemoryStore` | 会话持久化 |
| `WorkflowEngine` | `SequentialWorkflowEngine` | 注册 3 步诊断工作流 |
| `PromptRegistry` | `InMemoryPromptRegistry` | 自动加载 classpath 中的 `prompts/*.txt` |
| `LogProviderRegistry` | `InMemoryLogProviderRegistry` | 注册 3 种 LogProvider 实现 |
| `ProjectManager` | `ProjectManager` | 管理 Project/LogSource CRUD + 工具集构建 |
| `ModelClient` | `OpenAIModelClient` | 指向配置的 LLM endpoint |
| `BusinessExceptionAgent` | `BusinessExceptionAgent` | 领域 Agent（注入以上所有依赖） |

## OpenTelemetry 追踪

V0.5 集成 OpenTelemetry，自动导出到 Collector（`http://localhost:4318/v1/traces`）：

- **全局配置**：`management.tracing.sampling.probability=1.0`（开发环境全量）
- **端点 Span**：`/api/diagnosis` 和 `/api/chat` 创建主 Span + LLM 调用子 Span
- **响应字段**：`DiagnosisReport.traceId` 返回 trace ID，可在 Jaeger/Tempo 中检索对应请求
- **依赖**：`spring-boot-starter-actuator` + `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`

## Swagger UI

集成 SpringDoc OpenAPI，启动后访问 `http://localhost:8088/swagger-ui.html` 查看交互式 API 文档。

## 变更关注点

- **新增端点**：在 `agent-api/controller/` 新建 Controller，确保用 `@Operation` 注解 + Span 包裹
- **修改诊断流程**：`DiagnosisController` 和 `BusinessExceptionAgent` 的职责边界需同步审查
- **前端修改**：注意 EventBus 事件命名和 AppState 字段变更会影响多个组件
- **配置变更**：`AgentOpsConfig` 中的 Bean 装配顺序影响启动，新增依赖需在此注册
