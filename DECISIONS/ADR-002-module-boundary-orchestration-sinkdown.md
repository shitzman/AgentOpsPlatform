# ADR-002: 模块边界恢复 — 编排逻辑三层下沉

- **Status**: Accepted (2026-07-10)
- **Decider**: AgentOps Platform 维护者
- **Supersedes**: 无
- **Related**: ADR-001-javaparser-for-code-analysis.md

## Context

在 V1.0 DTO/VO 重构 + V1.0~V1.6 诊断工具循环迭代过程中，`ARCHITECTURE.md`
定义的四层边界被渐进侵蚀，三个模块都偏离了原始职责：

1. **`agent-api/DiagnosisService` 膨胀为"编排大脑"**：直接持有 `ModelClient`，
   内联 LLM 调用（`callLlm`）、自动工具循环（`runDiagnosisWithToolLoop`）、
   工具执行（`executeToolCall`）、多源上下文构建（`buildMultiSourceContext`）、
   报告 JSON 解析（`parseDiagnosisReport`）。单类承担了 runtime + domain + delivery
   三层职责。

2. **`business-exception-agent` 退化为栈解析骨架**：`BusinessExceptionAgent` 仅剩
   工作流步骤定义 + 一个占位 `diagnose()` 死代码（返回硬编码"待 LLM 分析"报告，
   从未被调用）。领域编排逻辑全部跑到 delivery 层。

3. **`agent-runtime` 名不副实**：只暴露 `ModelClient` 接口，没有"推理循环"这一
   runtime 的核心能力。AGENTS.md 写明 runtime 负责 "model orchestration and
   reasoning loop"，但实际上推理循环代码长在 `agent-api` 里。

**根因**：功能迭代（工具调用循环、多源上下文、可观测日志）时只关注"能跑通"，
没有用 `ARCHITECTURE.md` 的边界清单做回归校验，导致领域编排逻辑逐次向 delivery
层泄漏。每次增量都在 delivery 层"方便"地加代码，没有把编排能力沉淀回 runtime、
把领域流程沉淀回 domain。

## Decision

将编排逻辑按三层职责下沉，恢复 `delivery → domain → runtime` 的依赖方向：

- **`agent-runtime` 新增 `ReasoningLoop` 接口 + `DefaultReasoningLoop` 实现**：
  封装 `callLlm`（含 span + 耗时日志）、`runWithAutoToolLoop`（自动工具循环）、
  `executeToolCall`（工具执行，两重载）、`countToolRounds`（轮次统计）。
  provider-agnostic，不含任何领域逻辑，可被所有领域 Agent 复用。

- **`business-exception-agent` 新增 `DiagnosisOrchestrator`**：从 `DiagnosisService`
  下沉所有领域编排 — 诊断分支（堆栈/日志模式）、多源上下文构建、System Prompt 渲染、
  通过 `ReasoningLoop` 调用 LLM、LLM 输出 JSON 解析为 `DiagnosisReport`。不负责
  持久化、对话历史、HTTP 适配、tracing（这些留给 delivery）。

- **`agent-api/DiagnosisService` 瘦身为 delivery 适配层**：仅做入口校验、
  `ProjectEntity`→`ProjectInfo` 投影、委托 `DiagnosisOrchestrator.diagnose()`、
  持久化报告 + 保存对话历史、多轮追问 + 工具批准会话管理、span 打标。

**隔离机制**：引入 `ProjectInfo` record 作为 delivery→domain 的数据载体，
避免 `business-exception-agent` 依赖 `agent-repository` 的 `ProjectEntity`
（否则会导致 domain 反向依赖 delivery 层的持久化模型）。

## Consequences

**正面**：
- 边界清晰：runtime 通用编排、domain 领域流程、delivery HTTP 适配，各司其职。
- `ReasoningLoop` 可被未来领域 Agent（不止 Business Exception Agent）复用。
- 领域层可脱离 Spring/HTTP 独立单测（`DiagnosisOrchestratorTest` 已验证）。
- `DiagnosisService` 从 ~400 行编排大脑降为纯 delivery 适配，可读性提升。
- `BusinessExceptionAgent.diagnose()` 死代码及其产生的 write-only `engine` 字段、
  未用 import 一并清理，无残留技术债。

**负面**：
- 多一层间接（delivery 调 domain 调 runtime），调试时跳转多一层。
- 需要 `ProjectInfo` / `DiagnosisOutcome` 两个投影 record 维护字段同步。
- `agent-runtime` / `business-exception-agent` 新增 `spring-boot-starter-test`
  test 依赖（用于 Mockito 桩 `Tracer`/`ModelClient` 等复杂接口）。

## Alternatives Considered

1. **不下沉，维持现状** — 技术债持续累积：`DiagnosisService` 继续膨胀，第二个领域
   Agent 接入时会复制整套编排代码，`agent-runtime` 永远名不副实。已排除。

2. **在 `agent-api` 内拆 `DiagnosisService` 为内部类**（LLM 调用类 + 上下文类 + 解析类）
   — 边界仍在 delivery 层，`agent-runtime` 仍是空壳，无法复用，且不解决领域层
   依赖 `agent-repository` 的问题。已排除。

3. **引入 Spring StateMachine / LangChain4j 等编排框架** — 违反 AGENTS.md
   "Do not introduce heavyweight agent frameworks unless an architecture decision
   records the reason"。当前 `ReasoningLoop` 是 ~150 行的轻量接口+实现，
   无需引入框架。已排除。

## 教训（Lessons Learned）

- **功能迭代时必须用 `ARCHITECTURE.md` 边界清单做回归校验**：每次新增编排逻辑
  前先问"这属于 runtime / domain / delivery 哪一层"，不能因为 delivery 层
  "方便"就在那里堆代码。
- **死代码要尽早删除**：`BusinessExceptionAgent.diagnose()` 作为 V0.1 占位代码
  存留到 V1.7，期间误导了 openwiki 文档（误以为 LLM 调用在 `DiagnosisController`）。
- **接口先行**：`ReasoningLoop` 作为 SPI 接口先行定义，让领域层依赖接口而非
  `ModelClient` 实现，是避免反向依赖的关键。

## References

- 计划文件: `.trae/documents/restore-module-boundaries.md`
- `ARCHITECTURE.md` "Module Boundaries (V1.7)" 段
- AGENTS.md "Architecture Boundaries" 段
