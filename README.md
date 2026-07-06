# AgentOps Platform

AgentOps Platform 是一个面向企业研发团队的 Java AI Agent 智能研发平台。

当前第一个领域 Agent 是 **Business Exception Agent**：用于帮助研发团队分析线上业务异常，定位可能的 Root Cause，将异常现象关联到代码与 Git Commit，并逐步演进到生成安全的修复建议。

这个仓库不是 Demo，而是一个面向长期维护、持续扩展和商业化演进的平台型项目。

## 当前状态

项目当前处于 **V0.1 启动阶段**。

当前里程碑重点是先建立文档体系、项目结构和核心平台契约，再进入具体业务能力实现。

## 架构

平台采用四层架构：

- **Agent Runtime**：负责 Reasoning Loop、Tool Calling、Workflow、Streaming、Memory、Permission 等运行时能力。
- **Tool Layer**：所有外部能力都必须 Tool 化，Agent 不能绕过 Tool 直接访问数据库、Git、日志、指标或其他外部系统。
- **Domain Agent**：Business Exception Agent 负责诊断 Workflow、Prompt、Diagnosis Report 等领域能力。
- **Enterprise Plugin**：承载企业扩展能力，例如 GitLab、Jira、飞书、企业微信、Jenkins、SkyWalking 等。

详细设计见 `ARCHITECTURE.md`。

## 规划模块

- `agent-runtime`
- `agent-api`
- `agent-tools`
- `agent-memory`
- `agent-workflow`
- `agent-prompts`
- `agent-mcp`
- `business-exception-agent`

## 技术方向

- Java 21
- Spring Boot 3.x
- Maven 多模块工程
- OpenAI Java SDK
- MySQL
- Redis
- Docker Compose
- OpenTelemetry
- Prometheus
- Grafana

## AI 协作开发流程

为了让 Codex、Claude Code、Cursor 等 AI 工具能稳定接手并持续开发，本仓库维护以下协作文件：

- `PROJECT_BOOTSTRAP.md`：首次进入项目时使用的项目启动提示词。
- `CONTINUE_DEVELOPMENT.md`：日常开发时使用的任务推进提示词。
- `ARCHITECT_PROMPT.md`：架构调整前使用的架构评审提示词。
- `CODE_REVIEW_PROMPT.md`：代码审查提示词。
- `BUGFIX_PROMPT.md`：缺陷修复提示词。
- `REFACTOR_PROMPT.md`：重构提示词。
- `RELEASE_PROMPT.md`：发布准备提示词。
- `AGENTS.md`：AI Coding Agent 的仓库协作规则。
- `CLAUDE.md`：Claude Code 专用协作说明。

## 下一步

V0.1 已完成，继续推进 V0.2 实现阶段。



