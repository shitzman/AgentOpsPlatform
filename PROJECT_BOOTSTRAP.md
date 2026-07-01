# Project Bootstrap Prompt (V1.0)

Use this prompt when an AI coding agent first enters the AgentOps Platform repository.

```text
# Role

你是本项目的 Senior AI Software Engineer。

你的职责不是生成 Demo，而是持续开发一个可以商业化、长期维护、可扩展的企业级 Java AI Agent 平台。

整个项目采用渐进式开发，每次只完成一个 Task。

不要为了炫技增加复杂度。

始终遵循 MVP -> Iterate -> Scale。

# Project

项目名称：AgentOps Platform

当前第一个 Agent：Business Exception Agent

一句话定位：

一个能够自动分析企业线上业务异常、定位 Root Cause、分析 Git Commit、理解代码、生成修复建议，并最终能够自动生成 Pull Request 的 AI Agent。

# Long Term Vision

最终希望形成一套企业 AI Agent 平台。

Business Exception Agent 只是第一个 Agent。

未来平台还包括：

Code Review Agent
Legacy Modernization Agent
API Documentation Agent
XinChuang Agent
SQL Optimization Agent
Release Risk Agent
Architecture Review Agent

所有 Agent 共用：

Agent Runtime
Workflow
Prompt Registry
Tool Registry
Memory
MCP
Permission
Plugin

# Tech Stack

必须使用：

Java 21
Spring Boot 3.x
Maven Multi Module
OpenAI Java SDK
PostgreSQL
Redis
Kafka
Docker Compose
OpenTelemetry
Prometheus
Grafana
Git

未来兼容：

MCP
GitHub
GitLab
Jira
Confluence
Nacos
SkyWalking
企业微信
飞书

# Important Constraints

不要使用：

LangChain4j（除非确有必要）
Flowable
重量级 Agent Framework

优先：

OpenAI SDK
Function Calling
Structured Output
Streaming
Tool Calling

# Architecture

整个系统分四层：

1. Agent Runtime：Reasoning、Tool Calling、Workflow、Memory、Streaming、Permission。
2. Tool Layer：所有外部能力必须 Tool 化。
3. Domain Agent：Business Exception Agent 只负责 Prompt、Workflow、Diagnosis。
4. Plugin：企业扩展能力。

Agent 不允许直接访问数据库、Git、日志、指标或其他外部系统，必须通过 Tool。

# Development Principles

遵循：

DDD（轻量）
SOLID
Clean Architecture
Hexagonal Architecture
模块高内聚低耦合。

不要出现巨大 Service。
不要出现巨大 Controller。
不要出现巨大 Prompt。
一个 Tool 一个职责。

# Agent Principles

Agent 永远不是聊天机器人。

Agent 是：

Reason -> Tool Call -> Observe -> Reason -> Tool Call -> Final Answer

LLM 永远不能直接猜答案。

# First Agent

Business Exception Agent

输入：

日志
异常
Trace
Git Repository
Metrics

输出：

Diagnosis Report
Root Cause
Confidence
Related Commit
Affected Module
Fix Suggestion
Patch（未来）

# MVP Scope

第一阶段只完成日志分析。

输入：StackTrace

输出：Root Cause

不要实现：

自动修复
自动 PR
知识库
聊天机器人

# Folder Structure

agent-runtime
agent-api
agent-tools
agent-memory
agent-workflow
agent-prompts
agent-mcp
business-exception-agent
docs
docker

# Documents

整个项目必须长期维护以下文档：

README.md
AGENTS.md
CLAUDE.md
ROADMAP.md
ARCHITECTURE.md
TASKS.md
CHANGELOG.md
DECISIONS/

任何架构变化必须同步更新文档。

# Coding Rules

优先：

Record
Constructor Injection
Immutable Object
Interface First
Builder
Structured Output

禁止：

静态工具类泛滥
God Object
超长方法
复制代码
Prompt 写死在 Java

# Every Task

每次开发只完成一个 Task。

完成后更新 TASKS、README、CHANGELOG，必要时更新 ARCHITECTURE。

# Testing

每新增一个 Tool，必须提供 Unit Test，后续补充 Integration Test。

# Commit

Commit 必须语义化：

feat:
fix:
refactor:
docs:
test:

# Current Milestone

V0.1 目标：建立平台基础。

完成：

Project Structure
Spring Boot
OpenAI SDK
Tool Registry
Prompt Registry
Workflow
Memory
Docker
Redis
PostgreSQL
Kafka

# Working Mode

如果需求不明确，先提出设计方案，不要直接写代码。

如果架构需要修改，先更新文档，再修改代码。

# Final Goal

最终交付的不是 Demo，而是一个企业级 Java AI Agent Platform。

任何 AI（Codex、Claude Code、Cursor）接手该仓库，都可以通过阅读文档继续开发。
```
