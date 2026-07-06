# Tasks

## Current Milestone: V0.1 Platform Foundation

- [x] Create AI development prompt assets.
- [x] Create Maven multi-module project skeleton.
- [x] Add Java 21 and Spring Boot 3.x parent configuration.
- [x] Add module placeholders for runtime, API, tools, memory, workflow, prompts, MCP, and Business Exception Agent.
- [x] Define Tool Registry interfaces.
- [x] Define Prompt Registry interfaces.
- [x] Define lightweight Workflow interfaces.
- [x] Define Memory interfaces.
- [x] Add OpenAI Java SDK integration boundary.
- [x] Add Docker Compose baseline for MySQL, Redis, OpenTelemetry, Prometheus, and Grafana.
- [x] Add first Business Exception Agent log diagnosis workflow.

## V0.2 Log Diagnosis MVP

- [x] 实现 SimpleWorkflowContext（基于 LinkedHashMap）
- [x] 实现 SequentialWorkflowEngine（顺序执行器）
- [x] 实现 InMemoryToolRegistry（内存工具注册表）
- [x] 实现 InMemoryPromptRegistry（内存 Prompt 注册表 + classpath 加载）
- [x] 实现 InMemoryMemoryStore（内存存储）
- [x] 实现 OpenAIModelClient（HttpClient + Jackson，兼容 DeepSeek/通义千问）
- [x] 搭建 Spring Boot API 层（AgentOpsApplication + AgentOpsConfig + DiagnosisController）
- [x] 添加 application.yml 配置（支持环境变量切换 LLM 服务商）

## Backlog

- [ ] 实现 MySQL 版 MemoryStore（替换内存实现）
- [ ] 添加 Log Tool
- [ ] 添加 Git Tool
- [ ] 添加 repository search / blame / commit analysis
- [ ] 添加 Prometheus Tool
- [ ] 优化 DiagnosisReport 结构化输出（LLM JSON Mode）
- [ ] 添加单元测试
- [ ] OpenAPI / Swagger 文档
- [ ] 规划 GitHub 和 GitLab 插件边界



