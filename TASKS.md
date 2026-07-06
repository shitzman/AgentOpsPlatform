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

## V0.3 Diagnosis Quality Enhancement

- [x] JSON Mode 结构化输出（response_format: json_object）
- [x] 增强 DiagnosisReport：新增 severity / impactScope / urgency 维度
- [x] 优化诊断 System Prompt（嵌入 JSON Schema，新增诊断维度）
- [x] Controller 解析 LLM JSON 响应为 DiagnosisReport（含降级处理）
- [x] ChatRequest 新增 responseFormat 字段支持 JSON Mode
- [x] 添加单元测试：PromptTemplate / ToolRegistry / WorkflowEngine / MemoryStore（4 类 20 个用例）
- [x] 集成 Swagger / OpenAPI 文档（springdoc-openapi）

## V0.4 Tool Integration

- [x] Git Tool：git-log / git-blame / git-show（通过 ProcessBuilder 调本地 git）
- [x] Log Tool：log-search（模拟实现，可对接 ELK / Loki / SLS）
- [x] 多轮对话：conversationId + 历史加载/保存 + POST /api/chat 追问端点
- [x] AgentOpsConfig 启动时自动注册 4 个工具到 ToolRegistry

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



