# AgentOps Platform

面向企业研发团队的 Java AI Agent 智能研发平台。

第一个领域 Agent 是 **Business Exception Agent**：帮助研发团队分析线上业务异常，通过 LLM 定位 Root Cause，将异常现象关联到代码与上下文，并逐步演进到生成安全的修复建议。

## 项目状态

| 里程碑 | 状态 | 说明 |
|--------|:----:|------|
| V0.5 项目配置 + 可观测性 | ✅ 完成 | Project CRUD + 日志源 + Web 控制台 + OpenTelemetry |
| V1.0 Phase 1 持久化 | ✅ 完成 | MySQL + MyBatis-Plus + agent-repository 模块 |
| V1.0 Phase 2 数据采集 | ✅ 完成 | 环境采集 + Git 上下文 + 日志提取 |
| V1.0 Phase 3 多源诊断 | ✅ 完成 | 多源上下文注入 + 诊断报告持久化 + 历史查询 |
| V2.0 监控告警 | 🔲 待开始 | Prometheus Tool + 指标大盘 + 告警通知 |
| V3.0 自愈修复 | 🔲 待开始 | Patch 生成 + PR 创建 |

## 架构概览

```
┌──────────────────────────────────────────────────┐
│                   agent-api                       │
│         Spring Boot REST API 交付层               │
├──────────────────────────────────────────────────┤
│              business-exception-agent             │
│      领域 Agent：诊断 Workflow + Prompt + 报告     │
├────────────┬──────────┬──────────┬────────────────┤
│   runtime  │ workflow │ prompts  │    tools       │
│ 模型编排   │ 工作流   │ 模板管理 │  工具注册中心   │
├────────────┼──────────┴──────────┼────────────────┤
│  memory    │    repository        │   agent-mcp    │
│ 记忆抽象   │ MySQL+MyBatis-Plus   │  MCP 集成面    │
└────────────┴──────────────────────┴────────────────┘
```

## 模块说明

### 基础设施模块

| 模块 | 定位 | 核心接口 | 实现 |
|------|------|----------|------|
| **agent-tools** | 工具注册中心 | `ToolDefinition` / `ToolExecutor` / `ToolRegistry` | `InMemoryToolRegistry` |
| **agent-prompts** | Prompt 管理 | `PromptTemplate`（`{{变量}}` 渲染）/ `PromptRegistry` | `InMemoryPromptRegistry`（classpath 加载） |
| **agent-workflow** | 轻量工作流引擎 | `WorkflowContext` / `WorkflowStep` / `WorkflowEngine` | `SequentialWorkflowEngine` / `SimpleWorkflowContext` |
| **agent-memory** | 记忆存储 | `MemoryEntry` / `MemoryStore`（CRUD + 搜索） | `InMemoryMemoryStore` |

### 运行时模块

| 模块 | 定位 | 核心接口 | 实现 |
|------|------|----------|------|
| **agent-runtime** | 模型编排与调用 | `ModelClient` / `ChatMessage` / `ChatRequest` / `ChatResponse` | `OpenAIModelClient`（兼容 OpenAI/DeepSeek/通义千问） |

### 业务模块

| 模块 | 定位 | 核心类 | 说明 |
|------|------|--------|------|
| **agent-api** | REST API 交付层 | `DiagnosisController` / `AgentOpsConfig` | `POST /api/diagnosis` + `GET /api/health` |
| **business-exception-agent** | 领域 Agent | `BusinessExceptionAgent` / `StackTrace` / `DiagnosisReport` | 3 步诊断工作流：解析 → 过滤 → LLM 生成报告 |
| **agent-repository** | 数据持久化 | `ProjectEntity` / `MySqlMemoryStore` / `MySqlProjectManager` | MyBatis-Plus 实体 + Mapper |
| **agent-mcp** | MCP 集成 | 待开发 | Model Context Protocol 集成面 |

### 设计目的

- **工具注册中心 (agent-tools)**：所有外部能力（日志、Git、数据库）必须封装为 Tool。Agent 不能绕过注册中心直接访问外部系统。这样保证了可测试性、可替换性和安全审计。
- **Prompt 管理 (agent-prompts)**：Prompt 模板存储在 `resources/prompts/*.txt` 文件中，支持 `{{变量名}}` 占位符。避免大段 Prompt 硬编码在 Java 代码中，便于非开发人员（如 SRE）单独维护。
- **工作流引擎 (agent-workflow)**：轻量级顺序执行引擎，不引入 Activiti/Camunda 等重型框架。每一步是独立的 `WorkflowStep`，通过 `WorkflowContext` 传递数据。V0.1 仅线性执行，后续可扩展分支/并行/重试。
- **记忆存储 (agent-memory)**：统一的持久化抽象，V0.2 先用内存实现。后续对接 MySQL（表结构已在 `docker/mysql/init.sql` 中预定义），支持按类型分类和全文搜索。
- **模型调用 (agent-runtime)**：Provider-agnostic 的 `ModelClient` 接口，当前实现基于 `java.net.http.HttpClient` + Jackson 调用 OpenAI 兼容 API。可无缝切换 OpenAI / DeepSeek / 通义千问，只需改 `application.yml` 中的 `base-url` 和 `api-key`。
- **Business Exception Agent**：首个领域 Agent，核心是三步诊断工作流：(1) 解析堆栈提取结构化信息 (2) 过滤标记项目代码帧 (3) 渲染诊断 Prompt 调用 LLM 生成报告。

## 技术栈

- **语言**：Java 21
- **框架**：Spring Boot 3.5.7
- **构建**：Maven 多模块
- **数据库**：MySQL 8.0（开发用 H2/内存，生产用 MySQL）
- **缓存**：Redis 7
- **LLM**：OpenAI 兼容 API（DeepSeek / GPT-4o / 通义千问）
- **容器**：Docker Compose
- **可观测**：Prometheus + Grafana + OpenTelemetry

## 快速开始

### 1. 启动基础设施

```bash
docker compose -f docker/docker-compose.yml up -d
```

### 2. 配置 API Key

```powershell
# Windows PowerShell
$env:AGENTOPS_LLM_API_KEY = "sk-xxxxxxxx"
```

```bash
# Linux / macOS
export AGENTOPS_LLM_API_KEY="sk-xxxxxxxx"
```

### 3. 启动应用

```bash
mvn spring-boot:run -pl agent-api
```

### 4. 测试诊断

```bash
curl -X POST http://localhost:8080/api/diagnosis \
  -H "Content-Type: application/json" \
  -d '{"stackTrace": "java.lang.NullPointerException: Cannot invoke \"String.length()\"\n\tat com.agentops.service.OrderService.get(OrderService.java:42)\n\tat com.agentops.controller.OrderController.list(OrderController.java:28)"}'
```

### 5. 健康检查

```bash
curl http://localhost:8080/api/health
```

## 项目文档

| 文档 | 说明 |
|------|------|
| `AGENTS.md` | AI Coding Agent 协作规则 |
| `CLAUDE.md` | Claude Code 专用说明 |
| `ARCHITECTURE.md` | 四层架构详细设计 |
| `ROADMAP.md` | 版本路线图 |
| `TASKS.md` | 任务跟踪 |
| `CHANGELOG.md` | 变更日志 |
| `docs/RUNBOOK.md` | 运行与运维手册 |

## AI 协作提示词

本仓库为 Codex、Claude Code、Cursor 等 AI 工具维护了标准化的协作提示词文件：

- `PROJECT_BOOTSTRAP.md` — 首次进入项目时的启动提示词
- `CONTINUE_DEVELOPMENT.md` — 日常开发的任务推进提示词
- `ARCHITECT_PROMPT.md` — 架构评审提示词
- `CODE_REVIEW_PROMPT.md` — 代码审查提示词
- `BUGFIX_PROMPT.md` — 缺陷修复提示词
- `REFACTOR_PROMPT.md` — 重构提示词
- `RELEASE_PROMPT.md` — 发布准备提示词

## License

待定
