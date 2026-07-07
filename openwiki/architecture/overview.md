# 架构概览

AgentOps Platform 采用**四层架构**，严格分离关注点。每层拥有清晰的职责边界，由 `AGENTS.md` 规则强制执行。

## 分层架构图

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

执行引擎。负责模型编排和运行时机制：

- **推理循环** — 核心 ReAct 风格循环：推理 → 工具调用 → 观察 → 重复 → 最终答案
- **工具调用** — 通过 `ToolRegistry` 调用工具，编组参数和结果
- **流式输出** — 支持 LLM 提供商的流式响应
- **记忆访问** — 通过记忆抽象读写执行上下文
- **权限检查** — 在工具执行前进行安全控制
- **模型提供商抽象** — 封装 LLM 提供商（当前为 OpenAI/DeepSeek 兼容 API）

运行时**禁止**包含领域特定的诊断逻辑。

### 2. 工具层 (`agent-tools`)

所有外部能力以工具形式呈现。领域 Agent 不直接访问外部系统 — 它们通过 Tool Registry 调用工具。

**已实现的接口**（`agent-tools/src/main/java/com/agentops/tools/`）：

| 接口 | 用途 |
|-----------|---------|
| `ToolDefinition` | 不可变 record：`name`、`description`、JSON Schema `parameters` |
| `ToolExecutor` | `@FunctionalInterface` — `ToolResult execute(Map<String, Object> arguments)` |
| `ToolResult` | Record：`success` 布尔值、`output` 字符串（成功时）、`error` 字符串（失败时） |
| `ToolRegistry` | 中心 SPI：`register()`、`unregister()`、`getDefinition()`、`getExecutor()`、`listDefinitions()` |

**已实现的工具：**
- Git Tool — `git-log`（文件提交历史）、`git-blame`（逐行修改者）、`git-show`（commit 详情）
- Log Tool — `log-search`（按关键词/服务/时间范围搜索日志，支持可插拔日志源：文本输入/文件路径/Elasticsearch）

**日志源抽象（V0.5）：**
- `LogProvider` 接口 + `LogProviderRegistry` — 可插拔日志搜索后端
- 三种内置实现：`TextInputLogProvider` / `FileLogProvider` / `ElasticsearchLogProvider`

**项目配置管理（V0.5）：**
- `ProjectManager` — Project/LogSource CRUD + 项目专属工具注册表构建
- `FilteredToolRegistry` — 项目级工具过滤装饰器
- `ProjectController` — REST API（11 个端点）

**规划中的工具：**
- Prometheus Tool — 指标和告警查询
- Code Tool — 源码检查
- Database Tool — 生产数据库只读查询
- Redis Tool — 缓存状态检查
- Kubernetes Tool — Pod 和容器检查

### 3. 领域 Agent (`business-exception-agent`)

Business Exception Agent 是首个领域 Agent。它组合运行时、工作流、Prompt 和工具来实现：

1. 接收异常堆栈或异常事件
2. 执行诊断工作流
3. 通过工具查询日志、Git 历史记录和代码
4. 产出结构化的 `DiagnosisReport`，包含根因、置信度、关联提交和受影响模块
5. （未来）生成修复建议和补丁草案

领域 Agent 负责：
- 诊断工作流定义
- 领域专用 Prompt
- 诊断报告 Schema
- 根因分析策略

**不负责**底层集成 — 这些属于工具层。

### 4. 企业插件层

组织基础设施的扩展点。以插件形式设计，使平台适配不同企业：

- **IM 集成：** 飞书、企业微信、钉钉
- **版本控制集成：** GitHub、GitLab
- **CI/CD：** Jenkins
- **可观测性：** SkyWalking、Prometheus
- **配置中心：** Nacos
- **代码质量：** SonarQube

## Maven 模块映射

| 模块 | Artifact | Group: `com.agentops` | 状态 |
|--------|----------|-----------------------|--------|
| 根 POM | `agentops-platform` | 父聚合器 | 活跃 |
| `agent-runtime` | `agent-runtime` | 运行时引擎 | ✅ 已实现 |
| `agent-api` | `agent-api` | REST API | ✅ 已实现 |
| `agent-tools` | `agent-tools` | 工具合约 + 实现 | ✅ 已实现 |
| `agent-memory` | `agent-memory` | 记忆抽象 + 实现 | ✅ 已实现 |
| `agent-workflow` | `agent-workflow` | 工作流引擎 | ✅ 已实现 |
| `agent-prompts` | `agent-prompts` | Prompt 注册表 | ✅ 已实现 |
| `agent-mcp` | `agent-mcp` | MCP 集成 | 占位 |
| `business-exception-agent` | `business-exception-agent` | 领域 Agent | ✅ 已实现 |

所有模块继承自 `org.springframework.boot:spring-boot-starter-parent:3.5.7`，使用 Java 21。

## 技术栈

| 技术 | 角色 |
|------------|------|
| **Java 21** | 语言（records、pattern matching、virtual threads） |
| **Spring Boot 3.5.7** | 应用框架 |
| **Maven** | 构建和依赖管理 |
| **OpenAI 兼容 API** | LLM 提供商抽象（DeepSeek / 通义千问 / OpenAI） |
| **MySQL 8.0** | 持久化存储 |
| **Redis 7** | 缓存和会话状态 |
| **Docker Compose** | 本地开发基础设施 |
| **OpenTelemetry** | 分布式链路追踪（V0.5） |
| **Micrometer Tracing** | Span 管理与 OTLP 导出（V0.5） |
| **Prometheus** | 指标收集 |
| **Grafana** | 指标可视化 |
| **SpringDoc OpenAPI** | Swagger 文档 |

## 关键设计决策

1. **不使用重量级 Agent 框架。** 直接使用 OpenAI 兼容 SDK。除非有架构决策记录（存储于 `DECISIONS/`）支撑，否则避免引入 LangChain4j、Flowable 等。

2. **轻量级工作流引擎。** 构建自定义的、贴合需求的工作流引擎，而非采用 BPMN 引擎。工作流是确定性的 Agent 步骤序列，不是通用业务流程自动化。

3. **Prompt 作为资源而非代码。** Prompt 模板存放在 `agent-prompts` 或 classpath 资源中。禁止在 Java 类中硬编码 Prompt 字符串。`PromptRegistry` 支持 `{{变量名}}` 占位符渲染。

4. **LLM 响应的结构化输出。** 诊断报告和工具结果使用类型化 record，而非自由文本字符串。

5. **接口优先的模块边界。** 每个模块暴露定义其合约的 Java 接口（或接口集）。实现隐藏在这些接口之后。

6. **构造器注入。** 不使用字段注入。依赖关系在构造器中显式声明。

7. **Tool Registry 作为集成网关。** 领域 Agent 禁止绕过 Tool Registry。这是最重要的架构约束。

## 源码地图

| 内容 | 位置 |
|------|-------|
| 架构定义 | [`ARCHITECTURE.md`](../../ARCHITECTURE.md) |
| Tool Registry SPI | `agent-tools/src/main/java/com/agentops/tools/ToolRegistry.java` |
| Tool 定义 record | `agent-tools/src/main/java/com/agentops/tools/ToolDefinition.java` |
| Tool 执行器接口 | `agent-tools/src/main/java/com/agentops/tools/ToolExecutor.java` |
| Tool 结果 record | `agent-tools/src/main/java/com/agentops/tools/ToolResult.java` |
| Git Tool 实现 | `agent-tools/src/main/java/com/agentops/tools/GitTool.java` |
| Log Tool 实现 | `agent-tools/src/main/java/com/agentops/tools/LogTool.java` |
| Prompt Registry SPI | `agent-prompts/src/main/java/com/agentops/prompts/PromptRegistry.java` |
| Prompt 模板 record | `agent-prompts/src/main/java/com/agentops/prompts/PromptTemplate.java` |
| 工作流引擎 | `agent-workflow/src/main/java/com/agentops/workflow/SequentialWorkflowEngine.java` |
| 记忆存储 | `agent-memory/src/main/java/com/agentops/memory/InMemoryMemoryStore.java` |
| 模型客户端 | `agent-runtime/src/main/java/com/agentops/runtime/OpenAIModelClient.java` |
| REST API 入口 | `agent-api/src/main/java/com/agentops/api/AgentOpsApplication.java` |
| 诊断控制器 | `agent-api/src/main/java/com/agentops/api/controller/DiagnosisController.java` |
| Spring 配置 | `agent-api/src/main/java/com/agentops/api/config/AgentOpsConfig.java` |
| 应用配置 | `agent-api/src/main/resources/application.yml` |
| 诊断控制器 | `agent-api/src/main/java/com/agentops/api/controller/DiagnosisController.java` |
| 项目控制器 (V0.5) | `agent-api/src/main/java/com/agentops/api/controller/ProjectController.java` |
| Spring 配置 | `agent-api/src/main/java/com/agentops/api/config/AgentOpsConfig.java` |
| 应用配置 | `agent-api/src/main/resources/application.yml` |
| Web 控制台 (V0.5) | `agent-api/src/main/resources/static/` (7 文件：HTML + CSS + 5 JS) |
| 诊断 System Prompt | `business-exception-agent/src/main/resources/prompts/diagnosis-system.txt` |
| Project 模型 (V0.5) | `business-exception-agent/src/main/java/com/agentops/business/exceptionagent/model/Project.java` |
| ProjectManager (V0.5) | `business-exception-agent/src/main/java/com/agentops/business/exceptionagent/ProjectManager.java` |
| LogProvider 接口 (V0.5) | `agent-tools/src/main/java/com/agentops/tools/LogProvider.java` |
| LogProvider 实现 (V0.5) | `agent-tools/src/main/java/com/agentops/tools/TextInputLogProvider.java` 等 3 个 |
| LogProviderRegistry (V0.5) | `agent-tools/src/main/java/com/agentops/tools/LogProviderRegistry.java` |
| FilteredToolRegistry (V0.5) | `agent-tools/src/main/java/com/agentops/tools/FilteredToolRegistry.java` |
| 根 POM | [`pom.xml`](../../pom.xml) |
| 模块 POM | `agent-*/pom.xml` |
| Docker 基础设施 | `docker/docker-compose.yml` |
| OTel Collector 配置 | `docker/otel/otel-collector-config.yml` |
