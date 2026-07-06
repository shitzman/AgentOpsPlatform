# Changelog

All notable changes to AgentOps Platform will be documented in this file.

## Unreleased

### Added

- Added project bootstrap prompt for first-time AI agent onboarding.
- Added continue-development prompt for task-by-task implementation.
- Added architecture, review, bugfix, refactor, and release prompt assets.
- Added repository-level AI collaboration instructions in `AGENTS.md` and `CLAUDE.md`.
- Added initial roadmap, architecture, and task tracking documents.
- Added the initial Maven multi-module skeleton with root aggregator POM and module POMs.
- Added Java 21 and Spring Boot 3.5.7 parent build configuration.
- Added standard Maven source, resource, and test placeholder structure for all platform modules.
- Added Tool Registry interfaces: `ToolDefinition`, `ToolResult`, `ToolExecutor`, and `ToolRegistry`.
- Added Prompt Registry interfaces: `PromptTemplate` with `{{变量名}}` placeholder rendering, and `PromptRegistry`.
- Added lightweight Workflow interfaces: `WorkflowContext`, `WorkflowStep`, `WorkflowStepException`, `WorkflowDefinition`, and `WorkflowEngine`.
- Added Memory interfaces: `MemoryEntry` and `MemoryStore` with CRUD and search operations.
- Added OpenAI Java SDK integration boundary: `ChatMessage`, `ToolCall`, `ChatRequest`, `ChatResponse`, `ModelClient`, and `ModelClientException` in `agent-runtime`.
- Added Docker Compose baseline with PostgreSQL 16, Redis 7, Kafka (KRaft), Prometheus, Grafana, and OpenTelemetry Collector.
- Added Business Exception Agent with `StackTrace`, `StackTraceFrame`, `DiagnosisReport` models, 3-step diagnosis workflow, and diagnosis system prompt template.

