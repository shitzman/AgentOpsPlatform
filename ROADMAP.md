# Roadmap

## 项目愿景

AgentOps Platform 是一个面向中小型公司通用项目的**开源运维监控平台**。

通过收集业务项目的业务日志、运行环境、Git 代码、数据库等运行环境信息，帮助用户定位、发现、监控项目的运行情况。长期演进方向包括：根据 Bug 确定具体代码提交并生成修复分支（自愈修复）、根据业务量趋势生成优化建议（智能优化）。

## 当前里程碑

### V1.0 — 运维诊断平台（当前）

**定位**：面向中小型公司的故障诊断平台。接入目标项目的日志、Git 上下文、运行环境数据，通过 LLM 进行多源关联根因分析，输出结构化诊断报告。所有配置和历史基于 MySQL 持久化。

**核心目标**：
- 数据采集：日志 + Git 上下文 + 运行环境信息自动采集
- 故障诊断：多源关联的 LLM 根因分析（比 V0.5 单堆栈诊断更深入）
- 持久化：MySQL + MyBatis-Plus 替换全部内存实现，重启不丢数据

**分阶段执行**：

| Phase | 主题 | 关键交付 |
|-------|------|---------|
| Phase 1 | 持久化基础设施 | MySQL + MyBatis-Plus 实体/Mapper + MySqlMemoryStore + MySqlProjectManager |
| Phase 2 | 数据采集增强 | EnvironmentCollector + GitContextProvider + LogExtractor + DiagnosisContext |
| Phase 3 | 多源关联诊断 | 增强诊断 System Prompt + 多源上下文注入 + 诊断历史查询 API |

### V2.0 — 监控与告警（规划中）

- 指标大盘（Prometheus + Grafana 或自建）
- 异常检测规则引擎
- 多渠道告警通知（飞书 / 企业微信 / 钉钉）
- Prometheus Tool（PromQL 查询工具）
- Redis 缓存层
- 多租户 / 权限模型

### V3.0 — 自愈修复（规划中）

- Bug → Commit 自动定位
- 修复分支自动生成
- AI 自审 + 人工确认的 Patch Review 流程
- GitHub / GitLab PR 创建

### V4.0 — 智能优化（远期）

- 业务量趋势分析 → 容量规划建议
- 慢查询 / 慢接口自动检测 → 索引 / 缓存建议
- 性能回归检测

## 已完成的里程碑

### V0.5 — 可观测性上下文 + 项目配置管理（已完成）

- OpenTelemetry 链路追踪集成
- DiagnosisReport 关联 traceId / spanId
- 项目配置管理系统（Project / LogSource CRUD + 可插拔日志源）
- Web 控制台（SPA，3 个选项卡）
- FilteredToolRegistry 项目级工具过滤

### V0.4 — 工具集成（已完成）

- Git Tool（git-log / git-blame / git-show）
- Log Tool（log-search，可插拔）
- 多轮对话（conversationId + 追问端点）

### V0.3 — 诊断质量增强（已完成）

- JSON Mode 结构化输出
- 增强 DiagnosisReport（severity / impactScope / urgency）
- 20 个单元测试 + Swagger / OpenAPI

### V0.2 — 日志诊断 MVP（已完成）

- 端到端链路：POST 堆栈 → LLM 诊断 → JSON 报告
- 全部 6 个核心接口实现

### V0.1 — 平台基础（已完成）

- Maven 多模块骨架
- Tool/Prompt/Workflow/Memory 接口定义
- Docker Compose 基础设施
- Business Exception Agent 首个领域 Agent
