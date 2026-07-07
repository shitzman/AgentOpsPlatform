# 开发工作流

## AI 协作模型

本仓库设计为**AI 辅助、每次一个任务**的开发模式。该模型使用一组 Prompt 文件，为不同的开发场景定义特定的 AI 角色。

### 任务循环

```
1. 阅读治理文档（README, AGENTS, ARCHITECTURE, ROADMAP, TASKS）
2. 找到 TASKS.md 中第一个未勾选的任务
3. 仅实现该任务
4. 更新 TASKS.md ✅
5. 更新 CHANGELOG.md
6. 如有架构变更 → 同步更新 ARCHITECTURE.md
7. 使用语义化提交信息提交
8. 推送到 origin
```

此循环防止范围蔓延，使每次变更保持小型且可审查。

### Prompt 文件

每个 Prompt 文件将 AI 置于特定角色。开发者根据当前阶段选择正确的 Prompt。

| 文件 | 使用场景 |
|------|-------------|
| `PROJECT_BOOTSTRAP.md` | AI Agent 首次进入仓库 |
| `CONTINUE_DEVELOPMENT.md` | 正常的逐任务实现 |
| `ARCHITECT_PROMPT.md` | 结构/架构变更前 |
| `CODE_REVIEW_PROMPT.md` | 审查已完成代码 |
| `BUGFIX_PROMPT.md` | 修复缺陷 |
| `REFACTOR_PROMPT.md` | 计划性重构 |
| `RELEASE_PROMPT.md` | 发布准备 |

**注意：** Prompt 机制目前是手动的 — 开发者复制 prompt 文本粘贴到 AI 工具。只有 `CLAUDE.md` 可被 Claude Code 自动加载。

### AGENTS.md 与 CLAUDE.md

- **`AGENTS.md`** — 适用于所有 AI 编码 Agent（Codex、Cursor、Claude Code）的规则。定义模块边界、编码偏好、禁止模式、提交规范。
- **`CLAUDE.md`** — Claude Code 专用指令。被 Claude Code 自动加载。包含 JDK 21 的环境配置。

两个文件的重叠内容应保持同步。

## 环境配置

**需要 JDK 21。** 本机可能默认使用其他 JDK（如 JDK 17）。构建前需切换：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

验证：
```bash
mvn -version
# 预期输出: Java version: 21.0.6, vendor: Oracle Corporation
```

构建：
```bash
mvn clean compile      # 编译全部模块
mvn clean test         # 运行测试（当前 24 个用例）
mvn clean install      # 完整构建并安装到本地仓库
```

启动应用：
```powershell
# 需先设置 LLM API Key
$env:AGENTOPS_LLM_API_KEY = "sk-xxx"
# 切换到 JDK 21
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
# 启动
mvn spring-boot:run -pl agent-api
```

启动 Docker 基础设施（可选，用于 MySQL/Redis/Prometheus/Grafana/OTel）：
```bash
docker compose -f docker/docker-compose.yml up -d
```

## 编码规范

### 推荐模式
- **Java 21** 现代特性（records、sealed types、适时使用 pattern matching）
- **构造器注入** 优于字段注入
- **Record** 用于不可变数据传输对象
- **接口优先的模块边界** — 每个模块通过接口暴露合约
- **LLM 响应的结构化输出**
- **工具和核心工作流行为的单元测试**

### 禁止模式
- God Service（单个类承担过多职责）
- 臃肿的 Controller
- 静态工具类泛滥
- 领域 Agent 隐式访问外部系统
- Prompt 字符串散落在 Java 类中
- 未经计划的依赖添加

### 提交规范

```
feat: 添加 <功能>
fix: 修复 <问题>
refactor: 重构 <内容>
docs: 更新 <文档>
test: 添加 <测试内容> 的测试
chore: <杂项>
```

每个已完成任务单独提交。不要批量提交无关变更。

## Git 远程仓库

| 远程 | URL | 用途 |
|--------|-----|---------|
| `origin` | `https://gitee.com/shitzman/agent-ops-platform.git` | 主仓库（Gitee） |

完成任务后推送：
```bash
git push origin master
```

## 当前里程碑：V0.5 可观测性上下文

目标：打通 OpenTelemetry 链路追踪，支持跨服务根因分析。

**进度概览：**

| 里程碑 | 状态 |
|----------|------|
| V0.1 平台基础 | ✅ 完成 |
| V0.2 日志诊断 MVP | ✅ 完成 |
| V0.3 诊断质量增强 | ✅ 完成 |
| V0.4 工具集成 | ✅ 完成 |
| V0.5 可观测性上下文 | 🚧 进行中 |

**V0.5 已完成：**
- [x] 集成 OpenTelemetry（Trace 采集与上报）
- [x] 增强 DiagnosisReport：关联 traceId

**V0.5 待完成：**
1. 实现 Prometheus Tool（prometheus-query：按 PromQL 查询指标）
2. Trace 与指标关联分析
3. 诊断工作流支持跨服务调用链分析

权威任务清单见 [TASKS.md](../../TASKS.md)。

## 测试策略

项目期望：
- 新增工具实现时包含单元测试
- 核心工作流行为包含单元测试
- 测试位于对应模块的 `src/test/java` 下
- 当前 24 个测试用例分布在 4 个测试类中

## 修改代码时

- **添加新模块接口**：更新 `ARCHITECTURE.md`、`AGENTS.md` 边界描述和模块 `pom.xml`
- **添加领域 Agent**：在其自有模块下创建，保持薄层（组合原语），不添加直接外部访问
- **添加工具**：实现 `ToolExecutor`，在 `ToolRegistry` 中注册，添加单元测试
- **添加 Prompt**：存储在 `agent-prompts` 资源中，在 `PromptRegistry` 中注册
- **变更架构**：先运行 `ARCHITECT_PROMPT.md` 评审，更新 `ARCHITECTURE.md`，在 `DECISIONS/` 中记录决策
- **更新 OpenWiki**：架构或流程变更后同步更新 OpenWiki 文档
