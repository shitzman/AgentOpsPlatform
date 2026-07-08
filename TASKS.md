# Tasks

## V1.0 Phase 1 — 持久化基础设施

Goal: MySQL + MyBatis-Plus 替换所有 InMemory 实现，项目配置和对话历史重启不丢失。

### 模块结构

- [x] 新建 `agent-repository` 模块（pom.xml + 目录骨架）
- [x] 父 POM 注册 `agent-repository` 子模块 + MyBatis-Plus 版本管理
- [x] `agent-api/pom.xml` 添加 `agent-repository` 依赖

### Entity 层

- [x] `ProjectEntity` — 映射 `projects` 表
- [x] `LogSourceEntity` — 映射 `log_sources` 表
- [x] `ConversationEntity` — 映射 `conversations` 表
- [x] `DiagnosisReportEntity` — 映射 `diagnosis_reports` 表（表已存在）
- [x] `MemoryEntryEntity` — 映射 `memory_entries` 表（表已存在）

### Mapper 层

- [x] `ProjectMapper` extends BaseMapper<ProjectEntity>
- [x] `LogSourceMapper` extends BaseMapper<LogSourceEntity>
- [x] `ConversationMapper` extends BaseMapper<ConversationEntity>
- [x] `DiagnosisReportMapper` extends BaseMapper<DiagnosisReportEntity>
- [x] `MemoryEntryMapper` extends BaseMapper<MemoryEntryEntity>

### 持久化实现

- [x] `MySqlMemoryStore` implements MemoryStore（基于 MemoryEntryMapper）
- [x] `MySqlProjectManager` — 从 business-exception-agent 迁入，重写为基于 Mapper

### 配置与集成

- [x] 更新 `docker/mysql/init.sql` — 新增 projects / log_sources / conversations 表
- [x] `application.yml` 新增 MySQL 数据源配置
- [x] `application-local.yml` 新增本地 H2 开发配置
- [x] `AgentOpsConfig` — DataSource + MyBatis-Plus + Bean 切换
- [ ] 更新 `ProjectController` imports → 使用 MySqlProjectManager + ProjectEntity
- [ ] 更新 `DiagnosisController` — 对话历史使用 MySqlMemoryStore
- [ ] 移除旧代码：business-exception-agent 中的 ProjectManager + Project record
- [ ] 编译验证 + 单元测试

## V0.5 剩余待办（推迟至 V2）

- [ ] 实现 Prometheus Tool（prometheus-query：按 PromQL 查询指标）
- [ ] Trace 与指标关联分析
- [ ] 诊断工作流支持跨服务调用链分析

## V1.0 Phase 2 — 数据采集增强

- [x] EnvironmentCollector — 采集目标项目运行环境
- [x] GitContextProvider — 自动检测 Git 仓库上下文
- [x] LogExtractor — 从日志文件自动提取异常堆栈
- [x] DiagnosisContext 模型增强（聚合 stackTrace + logContext + gitContext + environment）
- [x] POST /api/projects/{id}/context — 项目完整上下文快照端点

## V1.0 Phase 3 — 多源关联诊断增强（待开始）

- [ ] 重写诊断 System Prompt（加入 Git blame + 日志上下文 + 环境变量分析引导）
- [ ] DiagnosisReport 新增 gitBlameHints / environmentFactors / logContextSummary 字段
- [ ] MultiSourceDiagnosisWorkflow（采集上下文 → 日志片段 → Git Blame → 渲染 → LLM）
- [ ] GET /api/diagnosis?projectId=X — 诊断历史分页查询
- [ ] 对话历史持久化查询与回溯

## Backlog

- [ ] Redis 版 MemoryStore（会话缓存 + 短期记忆，V2）
- [ ] Patch 方案生成工作流（V3）
- [ ] Reviewer 审核步骤（AI 自审 + 人工确认，V3）
- [ ] GitHub / GitLab 集成（V3）
- [ ] 飞书 / 企业微信 / 钉钉通知插件（V2）
- [ ] MCP 集成（待有明确使用场景）
