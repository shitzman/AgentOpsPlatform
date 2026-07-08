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
# 预期输出: Java version: 21.0.x
```

构建命令：
```bash
mvn clean compile      # 编译全部模块
mvn clean test         # 运行测试（当前 24 个用例）
mvn clean install      # 完整构建并安装到本地仓库
mvn spring-boot:run -pl agent-api   # 启动应用（端口 8088）
```

启动应用完整流程：
```powershell
# 1. 切换到 JDK 21
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 2. 设置 LLM API Key
$env:AGENTOPS_LLM_API_KEY = "sk-xxxxxxxx"

# 3. 启动
mvn spring-boot:run -pl agent-api
```

启动 Docker 基础设施（可选，用于 MySQL/Redis/Prometheus/Grafana/OTel）：
```bash
docker compose -f docker/docker-compose.yml up -d
```

详细环境配置（不同 LLM 提供商、IDE 运行、打包部署等）见 `docs/RUNBOOK.md`。

## 编码规范

### 推荐模式

- **Java 21** 现代特性（records、sealed types、适时使用 pattern matching）
- **构造器注入** 优于字段注入
- **Record** 用于不可变数据传输对象
- **接口优先的模块边界** — 每个模块通过接口暴露合约
- **LLM 响应的结构化输出** — JSON Mode（`response_format: json_object`）
- **工具和核心工作流行为的单元测试**

### 禁止模式

- God Service（单个类承担过多职责）
- 臃肿的 Controller
- 静态工具类泛滥
- 领域 Agent 隐式访问外部系统
- Prompt 字符串散落在 Java 类中
- 未经计划的依赖添加

## Git 工作流

### 双远程仓库

| Remote | URL | 用途 |
|--------|-----|------|
| `origin` | `https://gitee.com/shitzman/agent-ops-platform.git` | 主仓库（Gitee） |
| `github-origin` | `https://github.com/shitzman/AgentOpsPlatform.git` | 镜像仓库（GitHub） |

任务完成时推送到两个远程：
```bash
git push origin master
git push github-origin master
```

### 提交规范

语义化提交信息：

| 前缀 | 用途 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | 缺陷修复 |
| `refactor:` | 代码重构 |
| `docs:` | 文档更新 |
| `test:` | 测试添加或更新 |

示例：
```
feat: add Tool Registry interface

- Define Tool contract with ToolDefinition and ToolExecutor
- Add ToolRegistry SPI
- Add package-info.java for agent-tools module

Co-Authored-By: Claude <noreply@anthropic.com>
```

每个任务完成后提交，不要将无关变更合并到一个提交中。

## 测试指南

### 当前覆盖情况

| 模块 | 测试文件 | 用例数 | 覆盖内容 |
|------|------|:-----:|------|
| `agent-prompts` | `PromptTemplateTest.java` | 7 | 模板渲染、多变量、异常、空模板 |
| `agent-workflow` | `SequentialWorkflowEngineTest.java` | 6 | 步骤执行、Context 传递、错误处理 |
| `agent-tools` | `InMemoryToolRegistryTest.java` | 5 | 注册、重复、注销、列表、不存在 |
| `agent-memory` | `InMemoryMemoryStoreTest.java` | 6 | CRUD、搜索、ID 生成 |

### 缺失测试的模块

| 模块 | 需要添加的测试 |
|------|------|
| `agent-runtime` | `OpenAIModelClient` 的请求构建、JSON 解析、错误处理 |
| `agent-api` | Controller 集成测试（Mock LLM）、ProjectController CRUD |
| `business-exception-agent` | `BusinessExceptionAgent` 工作流、`ProjectManager` CRUD |
| `agent-tools` | `GitTool`、`LogTool`、各 `LogProvider` 实现、`FilteredToolRegistry` |

### 测试模式

测试使用 JUnit 5 + AssertJ。参考现有测试：

- `/agent-prompts/src/test/java/com/agentops/prompts/PromptTemplateTest.java` — 模板测试模式
- `/agent-workflow/src/test/java/com/agentops/workflow/SequentialWorkflowEngineTest.java` — 工作流测试模式
- `/agent-tools/src/test/java/com/agentops/tools/InMemoryToolRegistryTest.java` — 注册表测试模式

### 运行测试

```bash
# 全部测试
mvn test

# 单个模块
mvn test -pl agent-tools

# 单个测试类
mvn test -pl agent-tools -Dtest=InMemoryToolRegistryTest
```
