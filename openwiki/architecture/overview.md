# 架构概览

AgentOps Platform 采用**四层架构**，严格分离关注点。每层拥有清晰的职责边界，由 `AGENTS.md` 规则强制执行。

## 分层架构

```
┌──────────────────────────────────────────┐
│  企业插件层 (Enterprise Plugin)           │
│  GitLab, Jira, 飞书, 企业微信, Jenkins,   │
│  SkyWalking, Nacos, SonarQube...         │
├──────────────────────────────────────────┤
│  领域 Agent 层 (Domain Agent)             │
│  Business Exception Agent — 诊断         │
│  工作流, Prompt, 报告, 根因分析策略       │
├──────────────────────────────────────────┤
│  工具层 (Tool Layer)                      │
│  Log, Git, Code, DB, Redis, K8s,         │
│  Prometheus, Jira 工具...                │
├──────────────────────────────────────────┤
│  Agent 运行时 (Agent Runtime)             │
│  推理循环, 工具调用, 流式输出,            │
│  记忆管理, 权限检查, 模型提供商抽象       │
└──────────────────────────────────────────┘
```

## 各层职责

### 1. Agent 运行时 (`agent-runtime`)

执行引擎，负责模型编排和运行时机制。**禁止**包含领域特定的诊断逻辑。

**核心类**（`/agent-runtime/src/main/java/com/agentops/runtime/`）：

| 类型 | 文件 | 说明 |
|------|------|------|
| `ModelClient` 接口 | `ModelClient.java` | Provider-agnostic 模型调用边界 |
| `OpenAIModelClient` | `OpenAIModelClient.java` | 基于 HttpClient + Jackson 的实现，兼容 OpenAI/DeepSeek/通义千问 |
| `ChatRequest` | `model/ChatRequest.java` | 请求模型，支持 JSON Mode（`response_format: json_object`） |
| `ChatResponse` | `model/ChatResponse.java` | 响应模型 |
| `ChatMessage` | `model/ChatMessage.java` | 消息模型（system/user/assistant/tool 角色） |
| `ToolCall` | `model/ToolCall.java` | 工具调用请求/结果模型 |

**LLM 配置**（`/agent-api/src/main/resources/application.yml`）：
```yaml
agentops.llm.base-url: https://api.deepseek.com    # 通过 AGENTOPS_LLM_BASE_URL 覆盖
agentops.llm.api-key: ${AGENTOPS_LLM_API_KEY}       # 必填环境变量
agentops.llm.model: deepseek-v4-flash               # 通过 AGENTOPS_LLM_MODEL 覆盖
```

**待实现**：流式输出 `chatStream()`。

### 2. 工具层 (`agent-tools`)

所有外部能力（日志、Git、数据库）必须封装为 Tool。领域 Agent **禁止**绕过 ToolRegistry 直接访问外部系统。

**核心接口**（`/agent-tools/src/main/java/com/agentops/tools/`）：

| 类型 | 文件 | 说明 |
|------|------|------|
| `ToolDefinition` | `ToolDefinition.java` | 不可变 record：`name`、`description`、JSON Schema `parameters` |
| `ToolExecutor` | `ToolExecutor.java` | `@FunctionalInterface` — `ToolResult execute(Map<String, Object> args)` |
| `ToolResult` | `ToolResult.java` | Record：`success`、`output`（成功时）、`error`（失败时） |
| `ToolRegistry` | `ToolRegistry.java` | 中心 SPI：`register()`、`unregister()`、`getDefinition()`、`getExecutor()`、`listDefinitions()` |

**实现**：
- `InMemoryToolRegistry` — `ConcurrentHashMap` 实现，线程安全
- `FilteredToolRegistry` — 装饰器模式，按项目过滤可见工具

**已实现工具**：
- `GitTool` — `git-log`、`git-blame`、`git-show`（通过 ProcessBuilder 调本地 git）
- `LogTool` — `log-search`（支持可插拔日志源）

**日志源抽象（V0.5 新增）**：
- `LogProvider` 接口 + `LogProviderRegistry` — 可插拔日志搜索后端
- 三种实现：`TextInputLogProvider`（文本输入）、`FileLogProvider`（本地文件）、`ElasticsearchLogProvider`（模拟）
- `LogSourceType` 枚举：`TEXT_INPUT` / `FILE_PATH` / `ELASTICSEARCH`

详见 [业务异常诊断 Agent — 工具系统](../domain/business-exception.md#工具系统)。

### 3. 领域 Agent (`business-exception-agent`)

Business Exception Agent 是当前唯一的领域 Agent。组合运行时、工作流、Prompt 和工具：

1. 接收异常堆栈或异常事件
2. 执行 3 步诊断工作流
3. 通过工具查询日志、Git 历史记录和代码
4. 产出结构化 `DiagnosisReport`（根因、置信度、关联模块、修复建议）
5. 支持多轮追问（`POST /api/chat`，`conversationId` 关联上下文）

详见 [业务异常诊断 Agent](../domain/business-exception.md)。

### 4. 企业插件层

组织基础设施的扩展点。以插件形式设计，使平台适配不同企业：

- **IM 集成**：飞书、企业微信、钉钉
- **版本控制**：GitHub、GitLab
- **CI/CD**：Jenkins
- **可观测**：SkyWalking、Prometheus

## Maven 模块全景

| 模块 | 工件 | 状态 | 核心类 | 关键依赖 |
|------|------|:----:|------|------|
| `agent-runtime` | 模型调用 | ✅ | `OpenAIModelClient`, `ChatRequest`, `ChatResponse` | Jackson, HttpClient |
| `agent-tools` | 工具注册 | ✅ | `InMemoryToolRegistry`, `GitTool`, `LogTool`, `LogProvider` 系列 | — |
| `agent-workflow` | 工作流引擎 | ✅ | `SequentialWorkflowEngine`, `SimpleWorkflowContext` | — |
| `agent-memory` | 记忆存储 | ✅ | `InMemoryMemoryStore`, `MemoryEntry` | — |
| `agent-prompts` | Prompt 管理 | ✅ | `InMemoryPromptRegistry`, `PromptTemplate` | — |
| `agent-mcp` | MCP 集成 | ⬜ | `package-info.java` 占位 | — |
| `agent-api` | REST API | ✅ | `DiagnosisController`, `ProjectController`, `AgentOpsConfig` | Spring Boot Web, Actuator, OTel |
| `business-exception-agent` | 领域 Agent | ✅ | `BusinessExceptionAgent`, `ProjectManager` | 以上全部 |

模块间依赖关系：

```
agent-api
  ├── business-exception-agent
  │     ├── agent-runtime
  │     ├── agent-workflow
  │     ├── agent-prompts
  │     ├── agent-tools
  │     └── agent-memory
  └── Spring Boot (Web, Actuator, OpenTelemetry)
```

## 基础设施（Docker Compose）

由 `/docker/docker-compose.yml` 定义，`/docker/.env` 管理变量：

| 服务 | 端口 | 默认凭据 | 状态 |
|------|:----:|------|:----:|
| MySQL 8.0 | 3306 | `agentops` / `agentops123` | ✅ 含 init.sql 建表 |
| Redis 7 | 6379 | 无密码 | ✅ |
| Prometheus | 9090 | — | ✅ |
| Grafana | 3000 | `admin` / `admin` | ✅ |
| OpenTelemetry Collector | 4317 (gRPC) / 4318 (HTTP) | — | ✅ |

**MySQL 预定义表**（`/docker/mysql/init.sql`）：
- `memory_entries` — Agent 记忆存储（含 FULLTEXT 索引）
- `diagnosis_reports` — 诊断报告（含异常类型索引）

> **注意**：当前开发阶段使用内存实现（`InMemoryMemoryStore`），未对接 MySQL。MySQL 表已定义，生产切换时直接用。

## 关键设计决策

| 决策 | 原因 |
|------|------|
| Java 21 + Spring Boot 3.5.7 | 长期支持的现代 Java 生态 |
| 自建轻量工作流引擎 | 避免引入 Activiti/Camunda 等重型框架 |
| Maven 多模块（非微服务） | 当前阶段部署为单体应用，模块边界清晰即可 |
| Prompt 外置到文件 | 避免大段 Prompt 硬编码在 Java 中，便于 SRE 单独维护 |
| 工具注册中心强制隔离 | Agent 不能绕过注册中心访问外部系统，保证可测试性和安全性 |
| Provider-agnostic LLM 调用 | 切换 OpenAI/DeepSeek/通义千问只需改环境变量 |
| 内存存储先行 | 快速迭代，MySQL 表已预定义，生产切换用实现替换即可 |
| 全量 OTel 采样（开发环境） | `management.tracing.sampling.probability=1.0` |

## 变更关注点

- **新增工具**：在 `agent-tools` 中实现 `ToolDefinition` + `ToolExecutor`，在 `AgentOpsConfig` 中注册
- **新增 API 端点**：在 `agent-api/controller/` 中添加 Controller，注意 OpenTelemetry Span 包裹
- **修改诊断逻辑**：先看 `business-exception-agent/BusinessExceptionAgent.java` 工作流步骤，再看 `DiagnosisController.java` 中的 LLM 调用逻辑（两处需同步）
- **新增领域 Agent**：参考 `business-exception-agent` 模式，在 `business-*` 新模块中组合 runtime/workflow/prompts/tools
