# 业务异常诊断 Agent

Business Exception Agent 是 AgentOps Platform 的第一个（目前唯一的）领域 Agent。它组合运行时、工作流、Prompt 和工具，实现从 Java 异常堆栈到结构化诊断报告的端到端诊断链路。

## 模块位置

`/business-exception-agent/src/main/java/com/agentops/business/exceptionagent/`

| 文件 | 说明 |
|------|------|
| `BusinessExceptionAgent.java` | 核心领域 Agent：3 步诊断工作流 |
| `ProjectManager.java` | 项目配置管理：Project/LogSource CRUD + 工具集构建 |
| `package-info.java` | 模块声明 |
| `model/DiagnosisReport.java` | 诊断报告（LLM 结构化输出目标） |
| `model/StackTrace.java` | 解析后的堆栈模型 |
| `model/StackTraceFrame.java` | 单个堆栈帧 |
| `model/Project.java` | 被监控项目配置 |

## 诊断工作流

`BusinessExceptionAgent` 定义了 3 步顺序工作流（`WORKFLOW_NAME = "business-exception-diagnosis"`），运行在 `SequentialWorkflowEngine` 上：

### 第 1 步：解析堆栈（`parseStackTraceStep`）

**职责**：将原始堆栈文本解析为结构化数据。

**正则**：`at\s+([\w.$]+)\.([\w<>$]+)\(([^)]*)\)`

**输入**：`CTX_RAW_STACK_TRACE`（原始文本）

**输出**：`CTX_PARSED_TRACE`（`StackTrace` record）：
- `exceptionType`：异常全限定名（如 `java.lang.NullPointerException`）
- `message`：异常消息（可为 null）
- `frames`：`List<StackTraceFrame>`
- `rawText`：保留原始文本（用于注入 LLM Prompt）

### 第 2 步：过滤项目代码（`filterProjectCodeStep`）

**职责**：标记属于项目代码的堆栈帧（与框架/第三方库区分）。

**逻辑**：遍历所有帧，类名以 `com.agentops` 开头的标记 `isProjectCode = true`。

> **注意**：包前缀当前硬编码为 `com.agentops`。未来版本将改为可配置的项目包前缀列表。

### 第 3 步：生成报告（`generateReportStep`）

**职责**：组装上下文，准备 LLM 调用。

> **当前状态**：`BusinessExceptionAgent.diagnose()` 返回占位 `DiagnosisReport`。实际的 LLM 调用发生在 **`DiagnosisController`** 中（`agent-api` 模块），包括 Prompt 渲染、对话历史加载、JSON 响应解析。两个位置都需要审查以确保一致性。

### 工作流 Context Key 常量

```java
CTX_RAW_STACK_TRACE    = "rawStackTrace"    // String
CTX_PARSED_TRACE       = "parsedTrace"      // StackTrace
CTX_DIAGNOSIS_REPORT   = "diagnosisReport"  // DiagnosisReport
```

## 数据模型

### `DiagnosisReport`（Java record）

LLM JSON Mode 的结构化输出目标，同时也是 REST API 响应格式。

| 字段 | 类型 | 说明 |
|------|------|------|
| `summary` | String | 一句话异常摘要 |
| `exceptionType` | String | 异常全限定名 |
| `severity` | String | `critical` / `high` / `medium` / `low` |
| `likelyRootCause` | String | 根因分析 |
| `impactScope` | String | 影响范围（模块/服务/用户） |
| `urgency` | String | `立即修复` / `计划修复` / `低优先级` |
| `relatedModules` | List\<String\> | 关联模块列表 |
| `recommendations` | List\<String\> | 修复建议（按优先级排序） |
| `confidence` | double | 诊断置信度（0.0–1.0） |
| `traceId` | String | V0.5 新增：OpenTelemetry trace ID |

**约束**：`summary` 和 `exceptionType` 不可为空；`confidence` 范围 [0.0, 1.0]；列表字段返回不可变副本。

### `StackTrace`（Java record）

解析后的堆栈输入模型。

| 字段 | 类型 | 说明 |
|------|------|------|
| `exceptionType` | String | 异常类型 |
| `message` | String | 异常消息（nullable） |
| `frames` | List\<StackTraceFrame\> | 堆栈帧列表 |
| `rawText` | String | 原始堆栈文本（保留用于 LLM Prompt） |

工厂方法 `StackTrace.fromRaw(String)` 创建带 `exceptionType = "unknown"` 的占位实例。

### `StackTraceFrame`（Java record）

| 字段 | 类型 | 说明 |
|------|------|------|
| `className` | String | 全限定类名 |
| `methodName` | String | 方法名 |
| `fileName` | String | 源文件名（nullable） |
| `lineNumber` | int | 行号（-1 表示未知） |
| `isProjectCode` | boolean | 是否为应用代码（非框架） |

`toString()` 渲染为标准 Java 堆栈格式：`\tat class.method(file:line)`。

### `Project`（Java record）

被监控项目的配置数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | UUID |
| `name` | String | 项目名称 |
| `description` | String | 项目描述 |
| `gitRepoUrl` | String | Git 远程仓库 URL |
| `gitRepoLocalPath` | String | 本地仓库路径 |
| `enabledTools` | List\<String\> | 启用的工具名称列表 |
| `logSourceIds` | List\<String\> | 关联的日志源 ID 列表 |
| `createdAt` | Instant | 创建时间 |
| `updatedAt` | Instant | 更新时间 |

工厂方法 `Project.create(...)` 自动生成 UUID + 时间戳。`withUpdate(...)` 实现不可变式更新。

## ProjectManager

**源文件**：`/business-exception-agent/src/main/java/com/agentops/business/exceptionagent/ProjectManager.java`

核心项目管理服务，管理 `Project` 和 `LogSourceConfig` 实体的完整生命周期，持久化到 `MemoryStore`（序列化为 JSON）。

### 核心方法

| 方法 | 说明 |
|------|------|
| `createProject(name, desc, gitUrl, gitPath)` | 创建项目，返回 `Project` |
| `listProjects()` | 列出所有项目（从 MemoryStore 反序列化） |
| `getProject(id)` | 获取单个项目 |
| `updateProject(id, ...)` | 更新项目字段 |
| `deleteProject(id)` | 删除项目（级联删除关联日志源） |
| `enableTools(projectId, toolNames)` | 设置项目启用的工具 |
| `listAvailableTools()` | 列出全局可用工具名称 |
| `addLogSource(projectId, logSource)` | 添加日志源并关联到项目 |
| `getLogSources(projectId)` | 获取项目的所有日志源 |
| `updateLogSource(projectId, lsId, ...)` | 更新日志源 |
| `deleteLogSource(projectId, lsId)` | 删除日志源 |

### `buildProjectToolRegistry(projectId)`

核心方法：为指定项目构建**专属工具注册表**。流程：

1. 查询项目的 `enabledTools` 列表
2. 查询项目的日志源
3. 创建新的 `InMemoryToolRegistry`
4. 对每个启用的工具：如果是 Git 类工具，绑定项目的 Git 仓库路径；如果是 Log 类工具，绑定项目的日志源和 LogProvider
5. 返回项目专属的 `ToolRegistry`（供 `DiagnosisController` 在诊断时使用）

这实现了**多项目隔离**：不同项目看到不同的工具集和日志源。

## 诊断 System Prompt

**源文件**：`/business-exception-agent/src/main/resources/prompts/diagnosis-system.txt`

中文 System Prompt，角色设定为**资深 SRE + Java 后端专家**。

**注入变量**：
- `{{exceptionType}}` — 异常类型
- `{{exceptionMessage}}` — 异常消息
- `{{rawStackTrace}}` — 完整堆栈文本
- `{{projectContext}}` — V0.5 新增：项目上下文（git repo、启用的工具等）

**要求输出**：仅输出 JSON 对象，包含 8 个诊断维度（摘要、严重级别、根因、影响范围、紧急程度、关联模块、修复建议、置信度）。

**约束**：
- 聚焦项目代码（含 `com.agentops` 的包）
- 不确定的信息不编造

## 工具系统

工具系统分布在 `agent-tools` 模块中，由 `business-exception-agent` 中的 `ProjectManager` 编排使用。

### 工具注册中心

**接口体系**（`/agent-tools/src/main/java/com/agentops/tools/`）：

```
ToolRegistry (SPI)
  ├── register(ToolDefinition, ToolExecutor)
  ├── unregister(name)
  ├── getDefinition(name) → Optional<ToolDefinition>
  ├── getExecutor(name) → Optional<ToolExecutor>
  └── listDefinitions() → List<ToolDefinition>

ToolDefinition (record): name, description, parameters (JSON Schema)
ToolExecutor (@FunctionalInterface): execute(Map args) → ToolResult
ToolResult (record): success, output, error
```

**实现**：
- `InMemoryToolRegistry` — 线程安全（`ConcurrentHashMap`），核心实现
- `FilteredToolRegistry` — 装饰器，包裹一个注册表并只暴露 `enabledNames` 集合中的工具

### 已实现工具

#### GitTool

通过 `ProcessBuilder` 调用本地 `git` CLI。

| 工具名 | 参数 | 说明 |
|------|------|------|
| `git-log` | `filePath`（必填）、`maxCount`（默认 5） | 查看文件最近的提交历史 |
| `git-blame` | `filePath`、`lineNumber` | 查看指定行的最后修改者 |
| `git-show` | `commitHash` | 查看提交详情和 diff |

#### LogTool

| 工具名 | 参数 | 说明 |
|------|------|------|
| `log-search` | `keyword`（必填）、`service`、`timeRange`、`limit` | 按关键词搜索日志 |

两种模式：
- **全局模式**（无参构造）：返回模拟日志数据（向后兼容）
- **项目模式**（`LogTool(LogProvider, LogSourceConfig)`）：委托给真实日志源

### 可插拔日志源

**接口**（`/agent-tools/src/main/java/com/agentops/tools/`）：

| 类型 | 说明 |
|------|------|
| `LogProvider` 接口 | `search(Map args, LogSourceConfig)` + `supportedType()` |
| `LogProviderRegistry` 接口 | 按 `LogSourceType` 查找 Provider |
| `InMemoryLogProviderRegistry` | `ConcurrentHashMap` 实现 |
| `LogSourceType` 枚举 | `TEXT_INPUT` / `FILE_PATH` / `ELASTICSEARCH` |
| `LogSourceConfig` record | `id`, `name`, `type`, `properties: Map<String,String>`, `enabled`, `createdAt` |

**三种实现**：

| 实现 | 状态 | 搜索逻辑 |
|------|:----:|------|
| `TextInputLogProvider` | ✅ | 对粘贴的原始文本逐行关键词匹配 |
| `FileLogProvider` | ✅ | 扫描本地文件（最多 10000 行，UTF-8） |
| `ElasticsearchLogProvider` | ⚠️ | 当前为模拟实现，返回假数据；真实 ES `_search` API 待实现 |

## 变更关注点

- **修改诊断逻辑**：`BusinessExceptionAgent` 工作流步骤和 `DiagnosisController` 的 LLM 调用逻辑**需要同步修改**，当前职责边界模糊
- **新增工具**：在 `agent-tools` 实现，在 `AgentOpsConfig` 注册，在 `ProjectManager.buildProjectToolRegistry()` 添加绑定逻辑
- **新增日志源类型**：添加 `LogSourceType` 枚举值 + 实现 `LogProvider` 接口 + 在 `InMemoryLogProviderRegistry` 注册
- **修改数据模型**：`DiagnosisReport` 是 LLM JSON Schema 和 REST API 响应的**同一个结构**，修改字段需同步更新 `diagnosis-system.txt` 中的 JSON Schema
- **添加单元测试**：此模块当前 **0 个测试**，`BusinessExceptionAgent` 和 `ProjectManager` 是测试的优先目标
