# AgentOps Platform — OpenWiki 快速入门

AgentOps Platform 是一个面向企业工程团队的 **Java AI Agent 智能研发平台**。首个领域 Agent 是 **Business Exception Agent（业务异常诊断 Agent）**：接收 Java 堆栈 → LLM 根因分析 → 结构化诊断报告，未来将支持自动修复建议。

这不是一个 Demo — 这是为长期维护、渐进扩展和商业化设计的平台。

## 当前状态：V0.5

**V0.5 — 可观测性上下文 + 项目配置管理。** 端到端诊断链路已打通，已集成 OpenTelemetry 链路追踪。

| 模块 | 状态 | 核心能力 |
|------|:----:|------|
| `agent-tools` | ✅ | ToolRegistry 接口体系 + GitTool + LogTool + 可插拔日志源（3 种实现） |
| `agent-prompts` | ✅ | PromptTemplate 变量渲染 + 从 classpath 自动加载 prompt 文件 |
| `agent-workflow` | ✅ | SequentialWorkflowEngine + SimpleWorkflowContext（顺序执行） |
| `agent-memory` | ✅ | MemoryStore 接口 + InMemory 实现（支持 conversation 持久化） |
| `agent-runtime` | ✅ | OpenAIModelClient（HttpClient + Jackson，兼容 DeepSeek/OpenAI/通义千问）+ JSON Mode |
| `agent-api` | ✅ | Spring Boot REST API（15 个端点）+ Web 控制台（SPA，事件驱动架构） |
| `agent-mcp` | ⬜ | MCP 集成预留（仅有 package-info.java） |
| `business-exception-agent` | ✅ | 3 步诊断工作流 + ProjectManager + 结构化 DiagnosisReport |

## 技术栈

| 层 | 技术 |
|------|------|
| 语言 | Java 21（records、pattern matching） |
| 框架 | Spring Boot 3.5.7 + Maven 多模块 |
| LLM | OpenAI 兼容 API（DeepSeek / GPT-4o / 通义千问） |
| 数据库 | MySQL 8.0（开发用内存，表结构已定义） |
| 缓存 | Redis 7 |
| 可观测 | Prometheus + Grafana + OpenTelemetry Collector |
| 文档 | SpringDoc OpenAPI（Swagger UI） |

## 项目结构

```
AgentOpsPlatform/
├── pom.xml                          # Maven 父 POM，8 个子模块
├── agent-api/                       # Spring Boot REST API + Web 控制台
├── agent-runtime/                   # 模型调用客户端
├── agent-tools/                     # 工具注册中心 + Git/Log 工具 + 日志源抽象
├── agent-prompts/                   # Prompt 模板管理
├── agent-workflow/                  # 轻量工作流引擎
├── agent-memory/                    # 记忆存储抽象
├── agent-mcp/                       # MCP 集成（占位）
├── business-exception-agent/        # 领域诊断 Agent
├── docker/                          # Docker Compose + MySQL 初始化
├── docs/                            # 运行手册 + 开发模式评审
├── openwiki/                        # OpenWiki 文档（当前页面）
├── AGENTS.md                        # AI 编码 Agent 协作规则
├── CLAUDE.md                        # Claude Code 专用指令
├── ARCHITECTURE.md                  # 四层架构定义
├── ROADMAP.md                       # 版本路线图
├── TASKS.md                         # 活动任务清单（下一步做什么的唯一权威来源）
├── CHANGELOG.md                     # 变更日志
└── PROJECT_BOOTSTRAP.md             # 首次启动 Prompt
```

## 快速开始

### 前置条件

- JDK 21（`JAVA_HOME` 指向 JDK 21）
- Maven 3.8+
- LLM API Key（DeepSeek 推荐，国内可直接访问）

### 1. 切换到 JDK 21

```powershell
# Windows（开发机默认可能是 JDK 17）
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 验证
mvn -version
# 应输出：Java version: 21.0.x
```

### 2. 构建项目

```bash
# 首次构建：安装所有模块到本地 Maven 仓库
mvn install -DskipTests

# 日常编译
mvn clean compile

# 运行测试（当前 24 个用例）
mvn test
```

### 3. 启动应用

```powershell
# 设置 LLM API Key
$env:AGENTOPS_LLM_API_KEY = "sk-xxxxxxxx"

# 启动（端口 8088）
mvn spring-boot:run -pl agent-api
```

### 4. 验证

```bash
# 健康检查
curl http://localhost:8088/api/health

# 测试诊断
curl -X POST http://localhost:8088/api/diagnosis \
  -H "Content-Type: application/json" \
  -d '{"stackTrace": "java.lang.NullPointerException: ...\n\tat com.example.Service.method(Service.java:42)"}'

# Web 控制台
# 浏览器打开 http://localhost:8088/
# Swagger UI: http://localhost:8088/swagger-ui.html
```

### 5. （可选）启动基础设施

```bash
docker compose -f docker/docker-compose.yml up -d
# 启动 MySQL:3306, Redis:6379, Prometheus:9090, Grafana:3000, OTel:4317/4318
```

## 接下来去哪

| 页面 | 内容 |
|------|------|
| [架构概览](architecture/overview.md) | 四层架构、8 个 Maven 模块、基础设施、设计决策 |
| [开发工作流](development/workflow.md) | AI 协作模型、任务系统、编码规范、测试指南 |
| [REST API 与 Web 控制台](api/rest-api.md) | 全部 15 个端点、Web SPA 架构、OpenTelemetry 追踪 |
| [业务异常诊断 Agent](domain/business-exception.md) | 诊断工作流、数据模型、工具系统、项目管理 |

## 核心参考文档

进入项目时请按以下顺序阅读：

| # | 文件 | 用途 |
|---|------|---------|
| 1 | `README.md` | 项目概览和 tech stack |
| 2 | `AGENTS.md` | AI 编码 Agent 规则和模块边界 |
| 3 | `CLAUDE.md` | Claude Code 配置和开发约定 |
| 4 | `ARCHITECTURE.md` | 四层架构定义 |
| 5 | `TASKS.md` | 当前任务清单（下一步做什么的唯一权威来源） |
| 6 | `CHANGELOG.md` | 已完成工作日志 |
| 7 | `docs/RUNBOOK.md` | 运行与运维手册（Docker、MySQL、部署） |
