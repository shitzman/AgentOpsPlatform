# AgentOps Platform — OpenWiki 快速入门

AgentOps Platform 是一个面向工程团队的**企业级 Java AI Agent 智能开发平台**。首个领域 Agent 是 **Business Exception Agent（业务异常 Agent）**，负责分析生产环境异常、通过代码和 Git 历史追踪根因，未来将支持生成安全的修复建议。

这不是一个 Demo — 这是一个为长期维护、渐进扩展和商业化而设计的平台。

## 当前状态

**V0.5 — 可观测性上下文阶段。** 项目已完成 Maven 多模块骨架、全部核心接口定义与实现、端到端诊断链路、工具集成，正在打通 OpenTelemetry 链路追踪。

| 状态 | 模块 | 已实现内容 |
|--------|--------|--------------------|
| ✅ 完成 | `agent-tools` | `ToolRegistry`、`ToolDefinition`、`ToolExecutor`、`ToolResult` + `GitTool` + `LogTool` |
| ✅ 完成 | `agent-prompts` | `PromptRegistry`、`PromptTemplate` + InMemory 实现 + classpath 加载 |
| ✅ 完成 | `agent-workflow` | `SequentialWorkflowEngine` + `SimpleWorkflowContext` + 工作流接口 |
| ✅ 完成 | `agent-memory` | `MemoryStore` + `MemoryEntry` + InMemory 实现（支持 conversation 持久化） |
| ✅ 完成 | `agent-runtime` | `OpenAIModelClient`（HttpClient + Jackson）+ `ChatRequest`/`ChatResponse` + 多模型兼容 |
| ✅ 完成 | `agent-api` | Spring Boot REST API + `DiagnosisController` + Actuator + OpenTelemetry |
| ⬜ 占位 | `agent-mcp` | MCP 集成预留 |
| ✅ 完成 | `business-exception-agent` | 3 步诊断工作流 + `DiagnosisReport` + JSON Mode + System Prompt |

## 接下来去哪

- **[架构概览](architecture/overview.md)** — 四层架构、模块职责、技术选型、设计决策
- **[开发工作流](development/workflow.md)** — AI 协作开发模型、任务系统、Prompt 文件、环境配置、提交规范

## 核心参考文档

进入项目时请按以下顺序阅读：

| # | 文件 | 用途 |
|---|------|---------|
| 1 | [`README.md`](../README.md) | 项目概览和 tech stack |
| 2 | [`AGENTS.md`](../AGENTS.md) | AI 编码 Agent 规则和模块边界 |
| 3 | [`CLAUDE.md`](../CLAUDE.md) | Claude Code 配置和开发约定 |
| 4 | [`ARCHITECTURE.md`](../ARCHITECTURE.md) | 四层架构定义 |
| 5 | [`ROADMAP.md`](../ROADMAP.md) | V0.1–V0.5 路线图 |
| 6 | [`TASKS.md`](../TASKS.md) | 当前任务清单（下一步做什么的唯一权威来源） |
| 7 | [`CHANGELOG.md`](../CHANGELOG.md) | 已完成工作日志 |

### AI 协作 Prompt 文件

以下为不同开发场景的角色专用 Prompt：

| Prompt 文件 | 使用场景 |
|-------------|-------------|
| [`PROJECT_BOOTSTRAP.md`](../PROJECT_BOOTSTRAP.md) | 首次进入仓库 |
| [`CONTINUE_DEVELOPMENT.md`](../CONTINUE_DEVELOPMENT.md) | 日常逐任务实现 |
| [`ARCHITECT_PROMPT.md`](../ARCHITECT_PROMPT.md) | 结构/架构变更前 |
| [`CODE_REVIEW_PROMPT.md`](../CODE_REVIEW_PROMPT.md) | 代码评审 |
| [`BUGFIX_PROMPT.md`](../BUGFIX_PROMPT.md) | 缺陷修复 |
| [`REFACTOR_PROMPT.md`](../REFACTOR_PROMPT.md) | 计划性重构 |
| [`RELEASE_PROMPT.md`](../RELEASE_PROMPT.md) | 发布准备 |

## 项目结构一览

```
AgentOpsPlatform/
├── pom.xml                          # Maven 父 POM（Java 21, Spring Boot 3.5.7）
├── agent-runtime/                   # ✅ 模型编排、推理循环、工具调用
├── agent-api/                       # ✅ Spring Boot REST API 模块
├── agent-tools/                     # ✅ Tool Registry 合约 + Git/Log 工具
├── agent-memory/                    # ✅ Memory 接口 + InMemory 实现
├── agent-workflow/                  # ✅ Workflow 引擎 + 顺序执行器
├── agent-prompts/                   # ✅ Prompt Registry + classpath 加载
├── agent-mcp/                       # MCP 集成（占位）
├── business-exception-agent/        # ✅ 领域诊断 Agent + 3 步工作流
├── docker/                          # Docker Compose 基础设施
├── openwiki/                        # OpenWiki 文档
├── AGENTS.md                        # AI 编码 Agent 协作规则
├── CLAUDE.md                        # Claude Code 专用指令
├── ARCHITECTURE.md                  # 架构定义
├── ROADMAP.md                       # 版本路线图
├── TASKS.md                         # 活动任务清单
├── CHANGELOG.md                     # 变更日志
└── PROJECT_BOOTSTRAP.md             # 项目启动 Prompt
```

## 快速开始：构建

**前置条件：** JDK 21, Maven 3.9+

```powershell
# Windows 上 JDK 21 非默认时需先切换：
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 验证
mvn -version

# 编译全部模块
mvn clean compile

# 运行测试（当前 24 个用例）
mvn test

# 启动应用（需设置 LLM API Key）
$env:AGENTOPS_LLM_API_KEY = "sk-xxx"
mvn spring-boot:run -pl agent-api
```

启动后访问：
- API 测试页面：`http://localhost:8088/`
- Swagger 文档：`http://localhost:8088/swagger-ui.html`
- Actuator 健康检查：`http://localhost:8088/actuator/health`

## 核心设计规则

1. **Agent 必须通过 Tool Registry 调用工具。** 领域 Agent 禁止直接访问数据库、Git、日志、指标或外部系统。
2. **Prompt 存于 Java 代码之外。** Prompt 存储在 `agent-prompts` 的 classpath 资源中。
3. **一次只做一个任务。** 只处理 `TASKS.md` 中第一个未完成的任务。
4. **不使用重量级框架。** 直接使用 OpenAI Java SDK。除非有架构决策记录支撑，否则不引入 LangChain4j、Flowable 等。
5. **接口优先的模块边界。** 所有模块边界（runtime、tools、workflow、memory）均先定义接口后实现。
