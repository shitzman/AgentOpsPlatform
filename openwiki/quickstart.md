# AgentOps Platform — OpenWiki 快速入门

AgentOps Platform 是一个面向企业工程团队的 **Java AI Agent 智能运维平台**。首个领域 Agent 是 **Business Exception Agent（业务异常诊断 Agent）**：接收 Java 异常堆栈或应用日志 → LLM 多源关联根因分析 → 结构化诊断报告，支持多轮追问和人在回路工具调用。

这不是一个 Demo — 是为长期维护、渐进扩展和商业化设计的平台。

## 当前状态：V1.0

**V1.0 — 运维诊断平台。** 端到端多源关联诊断链路已打通：日志 + Git 上下文 + 运行环境 → LLM 根因分析 → 持久化报告。已集成人在回路工具调用循环。

| 模块 | 状态 | 核心能力 |
|------|:----:|------|
| `agent-api` | ✅ | Spring Boot REST API（20+ 端点）+ SPA Web 控制台（5 选项卡）+ Service 层编排 |
| `agent-runtime` | ✅ | OpenAIModelClient（HttpClient + Jackson，兼容 DeepSeek/OpenAI/通义千问）+ JSON Mode |
| `agent-tools` | ✅ | ToolRegistry 接口体系 + GitTool + LogTool + 3 种日志源实现 + 上下文采集器 |
| `agent-prompts` | ✅ | PromptTemplate 变量渲染 + classpath 自动加载 |
| `agent-workflow` | ✅ | SequentialWorkflowEngine + SimpleWorkflowContext（顺序执行） |
| `agent-memory` | ✅ | MemoryStore 接口 + InMemory 实现 |
| `agent-repository` | ✅ | MySQL + MyBatis-Plus 持久化（V1.0 新增，6 张表、5 个 Mapper、3 个 Store/Manager） |
| `agent-mcp` | ⬜ | MCP 集成预留（仅有 package-info.java） |
| `business-exception-agent` | ✅ | 3 步诊断工作流 + DiagnosisContext 多源模型 + 结构化 DiagnosisReport |

## 技术栈

| 层 | 技术 |
|------|------|
| 语言 | Java 21（records、pattern matching） |
| 框架 | Spring Boot 3.5.7 + Maven 9 模块 |
| LLM | OpenAI 兼容 API（DeepSeek / GPT-4o / 通义千问） |
| 数据库 | 开发：H2（MySQL 兼容模式）；生产：MySQL 8.0 + MyBatis-Plus 3.5.10 |
| 缓存 | Redis 7 |
| 可观测 | Micrometer Tracing + OpenTelemetry OTLP exporter |
| 文档 | SpringDoc OpenAPI（Swagger UI） |

## 项目结构

```
AgentOpsPlatform/
├── pom.xml                          # Maven 父 POM，9 个子模块
├── agent-api/                       # Spring Boot 入口 + REST API + Web 控制台 + Service 层
├── agent-runtime/                   # LLM 模型调用客户端
├── agent-tools/                     # 工具注册中心 + Git/Log 工具 + 日志源抽象 + 上下文采集
├── agent-prompts/                   # Prompt 模板管理
├── agent-workflow/                  # 轻量工作流引擎
├── agent-memory/                    # 记忆存储抽象
├── agent-repository/                # MySQL + MyBatis-Plus 持久化层（V1.0 新增）
├── agent-mcp/                       # MCP 集成（占位）
├── business-exception-agent/        # 领域诊断 Agent
├── docker/                          # Docker Compose + MySQL 初始化
├── docs/                            # 运行手册
├── AGENTS.md                        # AI 编码 Agent 协作规则
├── CLAUDE.md                        # Claude Code 专用指令
├── ARCHITECTURE.md                  # 四层架构定义
├── ROADMAP.md                       # 版本路线图
├── TASKS.md                         # 任务清单（下一步做什么的权威来源）
├── CHANGELOG.md                     # 变更日志
└── PROJECT_BOOTSTRAP.md             # 首次启动 Prompt
```

## 核心功能

### 诊断模式

| 模式 | 输入 | 说明 |
|------|------|------|
| **堆栈诊断** | Java 异常堆栈 | 解析堆栈 → 提取项目代码帧 → 注入 Git + 环境 + 日志上下文 → LLM 根因分析 |
| **纯日志分析** | 应用日志文本（无堆栈） | 扫描 ERROR/WARN 模式 → 频率/时间分析 → LLM 诊断（log-only 模式，V1.0 新增） |

### 人在回路工具调用（V1.0 新增）

追问时 LLM 可请求工具调用（如 git-log、log-search）。前端展示待批准工具卡片，用户勾选/编辑参数后批准执行，结果回传 LLM 继续推理循环（上限 8 轮）。

### 多源上下文注入（V1.0 新增）

诊断时自动采集并注入：
- **环境信息**：JDK 版本、OS、JVM 内存、CPU 核心（`EnvironmentCollector`）
- **Git 上下文**：当前分支、最近提交、相关文件的 blame 信息（`GitContextProvider`）
- **日志上下文**：从关联日志源拉取的关联日志（`LogProvider` 可插拔后端）

### 日志源类型

| 类型 | 实现 | 说明 |
|------|------|------|
| `TEXT_INPUT` | `TextInputLogProvider` | 在 UI 中粘贴文本日志 |
| `FILE_PATH` | `FileLogProvider` | 本地日志文件 + 文件上传（V1.0 新增） |
| `ELASTICSEARCH` | `ElasticsearchLogProvider` | ES `_search` API（Basic Auth / API Key，V1.0 重写为真实查询） |

## 快速开始

### 前置条件

- JDK 21（`JAVA_HOME` 指向 JDK 21）
- Maven 3.8+
- LLM API Key（DeepSeek 推荐）

### 1. 切换到 JDK 21

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 验证
mvn -version
# 应输出：Java version: 21.0.x
```

### 2. 构建项目

```bash
# 首次构建
mvn install -DskipTests

# 日常编译
mvn clean compile

# 运行测试（当前 44 个用例）
mvn test
```

### 3. 启动应用

```powershell
# 设置 LLM API Key
$env:AGENTOPS_LLM_API_KEY = "sk-xxxxxxxx"

# 开发模式启动（H2 内存数据库，端口 8088）
mvn spring-boot:run -pl agent-api
```

### 4. 启动基础设施（可选）

```bash
# 启动 MySQL + Redis + Prometheus + Grafana
docker compose -f docker/docker-compose.yml up -d

# 使用 MySQL 启动应用
mvn spring-boot:run -pl agent-api -Dspring-boot.run.profiles=mysql
```

### 5. 访问

| 服务 | URL | 说明 |
|------|-----|------|
| Web 控制台 | http://localhost:8088/ | SPA 工作台（5 选项卡） |
| Swagger UI | http://localhost:8088/swagger-ui.html | 交互式 API 文档 |
| Health API | http://localhost:8088/api/health | 健康检查 |

## 文档导航

| 文档 | 内容 |
|------|------|
| [架构概览](architecture/overview.md) | 分层架构、模块职责、依赖关系、Service 层 |
| [REST API 与 Web 控制台](api/rest-api.md) | 全部端点、DTO/VO 规范、前端架构 |
| [业务异常诊断 Agent](domain/business-exception.md) | 诊断工作流、数据模型、Prompt、工具系统 |
| [开发工作流](development/workflow.md) | AI 协作模型、环境配置、编码规范、测试指南 |
