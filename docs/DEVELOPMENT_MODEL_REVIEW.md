# 开发模式评审与改进建议

> 2026-07-02 | V0.1 阶段中期评审

---

## 一、当前模式总览

AgentOps Platform 采用的是一套 **"文档驱动 + AI 协作 + 渐进式交付"** 的开发模式。

### 1.1 核心机制

```
文档体系 (7 个持续维护文档)
     │
     ▼
角色 Prompt (7 种场景化 Prompt)
     │
     ▼
单任务循环 (TASKS.md → 实现 → 更新文档 → 下一个)
     │
     ▼
架构约束 (四层架构 + 硬性禁止项)
```

### 1.2 资产清单

| 类别 | 文件数 | 内容 |
|------|--------|------|
| 规划文档 | 6 | README / ARCHITECTURE / ROADMAP / TASKS / CHANGELOG / DECISIONS |
| AI 协作规则 | 2 | AGENTS.md / CLAUDE.md |
| 角色 Prompt | 7 | Bootstrap / ContinueDev / Architect / CodeReview / Bugfix / Refactor / Release |
| Maven 模块 | 8 | runtime / api / tools / memory / workflow / prompts / mcp / business-exception-agent |
| 源代码 | 8 个 `package-info.java` | 全部为占位文件 |

---

## 二、亮点（值得保留）

### 2.1 Prompt 场景化拆分

7 种 Prompt 覆盖了从首次进入到发布检查的完整生命周期，每个 Prompt 都有明确的角色定位、输入要求、输出格式。这不是简单地"写个 Prompt 让 AI 写代码"，而是把 AI 当作**不同场景下的不同角色**来使用。

**价值**：AI 在 Code Review 模式下不会越界去重构，在 Bugfix 模式下不会顺手加功能。

### 2.2 "任何 AI 可接力"的设计理念

文档体系的设计目标明确写在 PROJECT_BOOTSTRAP.md 里：

> 任何 AI（Codex、Claude Code、Cursor）接手该仓库，都可以通过阅读文档继续开发。

这意味着文档不是给人看的说明书，而是**AI 的共享工作记忆**。这是一个前瞻性的设计决策。

### 2.3 架构约束前置

四层架构和禁止项（Agent 不允许绕过 Tool、不允许 God Service、不允许 Prompt 硬编码）在写第一行业务代码之前就已经定义清楚。这比写到一半再重构要高效得多。

### 2.4 一次一个任务

单任务循环阻止了 AI 的"顺手优化"冲动，保持每次变更小且可审查。

---

## 三、问题与风险

### 3.1 【严重】文档过度前置，代码零产出

当前项目状态：
- 13 个文档/Prompt 文件
- 8 个 Maven 模块
- **0 行业务代码**

项目的 TASKS.md 前 4 项全部是文档类任务。在 V0.1 已经过半的情况下，连一个 Tool 接口都还没定义。这是典型的**分析瘫痪（Analysis Paralysis）**——用"把文档写好"来推迟真正困难的实现决策。

**建议**：
- 立即冻结文档体系的新增，现有文档够用了
- 接下来 3 个 Task 必须全是代码（Tool Registry → Prompt Registry → Workflow Engine）
- 在实现过程中发现文档不足时再补充，而不是反过来

### 3.2 【中等】Prompt 文件机制的脆弱性

当前 Prompt 文件的结构是：

```markdown
# Some Prompt Name
Use this prompt when ...
```text
你是本项目的 Senior AI Software Engineer。
...
```

这意味着**需要人手动复制 Prompt 文本粘贴给 AI**。这个流程：
- 没有自动化
- 依赖人记住"当前场景该用哪个 Prompt"
- 不同 AI 工具（Claude Code vs Cursor vs Codex）的 Prompt 注入机制不同

**建议**：
- CLAUDE.md 已经解决了 Claude Code 的场景——它会自动加载
- 对于其他 AI 工具，考虑在 `AGENTS.md` 顶部用更短的指引替代长 Prompt："如果你是 Code Review 场景，重点关注 X/Y/Z"
- 长期来看，考虑把 Prompt 模板放到 `agent-prompts` 模块里，做成可被 Runtime 加载的资源

### 3.3 【中等】单任务循环过于机械

"只做 TASKS.md 第一个未完成任务"这条规则的问题：

- **Tool Registry、Prompt Registry、Memory 接口这三者是独立的**，可以并行开发
- 严格串行会让 V0.1 的时间线拉得很长
- 如果第一个任务被阻塞（比如设计决策未定），整个项目停滞

**建议**：
- 改为："优先完成 TASKS.md 中第一个未完成任务；如果该任务被阻塞，选择一个无依赖的未完成任务继续"
- 在 TASKS.md 中标注任务间的依赖关系

### 3.4 【中等】中英混杂，风格不统一

| 文件 | 语言 |
|------|------|
| README.md | 中文为主 |
| ARCHITECTURE.md | 英文 |
| AGENTS.md | 英文 |
| CLAUDE.md | 英文 |
| PROJECT_BOOTSTRAP.md | Prompt 文本是中文 |
| 代码（package-info.java） | 英文 |

这种不一致会导致：
- AI 在不同文件中切换时产生语言混乱
- 开源协作时英文用户读不懂 README

**建议**：
- 代码、注释、架构文档统一英文
- README 和面向用户的文档提供中英双语或单独一份英文版
- AI 角色 Prompt 跟代码交互的部分用英文，领域解释用中文

### 3.5 【轻微】AGENTS.md 和 CLAUDE.md 职责重叠

两个文件都在定义：
- 项目是什么
- 架构边界
- 编码规范
- 禁止事项

区别仅在于 CLAUDE.md 多了一个 Environment Setup。当一个规则需要修改时，可能只改了一个文件忘了另一个。

**建议**：
- `AGENTS.md` 作为通用规则（所有 AI 工具共享）
- `CLAUDE.md` 只放 Claude Code 特有配置（环境设置、hook 配置等），用 `请遵循 AGENTS.md 中的通用规则` 引用

### 3.6 【轻微】缺少可运行的最小验证

项目编译通过（`mvn compile`），但：
- 没有 `agent-api` 的 Spring Boot 启动类
- 没有 `/actuator/health` 可验证
- 没有 Docker Compose 文件
- TASKS.md 中 Docker Compose 排在第 10 位

在 8 个模块之间定义接口之前，**先让一个模块能跑起来**（哪怕只是一个 health endpoint）会大幅提升信心。

**建议**：
- 把"agent-api 模块能启动并响应 /actuator/health"提前到 TASKS.md 第 5 位
- 或者至少在第 5-8 位接口定义任务中穿插一个可运行的验证点

### 3.7 【轻微】"不用 LangChain4j"的决定为时过早

ARCHITECTURE.md 和 PROJECT_BOOTSTRAP.md 都明确写了不要 LangChain4j、不要 Flowable。但项目**目前一行 OpenAI SDK 调用的代码都没写**。

在真正接入 OpenAI SDK 之前，无法判断：
- 裸 SDK 的 Tool Calling 开发体验如何
- Structured Output 的实现是否足够方便
- 是否需要轻量封装

**建议**：
- ARCHITECTURE.md 中的这条改为："优先使用 OpenAI Java SDK 直接集成；如后续开发体验证明需要轻量封装，通过架构决策记录引入"
- 把"不用 LangChain4j"从禁止项降级为优先级指引

### 3.8 【轻微】双远程仓库维护成本

Gitee + GitHub 双推送在当前 V0.1 阶段增加了每次提交的操作步骤，而项目现在没有外部协作者，GitHub Mirror 的价值尚未体现。

**建议**：
- 保留双远程配置（已经配好了，删掉反而浪费）
- GitHub 侧可以在 README 上加一个 badge 指向 Gitee 主仓库
- 等有实质代码后再考虑 GitHub Actions CI

---

## 四、改进优先级

| 优先级 | 行动 | 预期效果 |
|--------|------|----------|
| **P0** | 立即开始写代码，冻结新文档创建 | 打破分析瘫痪，进入实现阶段 |
| **P0** | 接下来 2 个 Task 产出 Tool Registry + Prompt Registry 接口 | V0.1 有实质性进展 |
| **P1** | CLAUDE.md 瘦身，只保留 Claude Code 特有内容 | 减少维护负担 |
| **P1** | 在 TASKS.md 中标注任务依赖关系 | 允许并行开发 |
| **P2** | 提前插入一个可运行的验证点 | 建立信心 |
| **P2** | 统一文档语言策略 | 降低 AI 混淆风险 |
| **P3** | Prompt 文件机制优化 | 长期工程化 |

---

## 五、总结

这套开发模式的设计意图是好的——用文档消除 AI 协作中的信息不对称，用架构约束防止 AI 写烂代码，用单任务循环保持每次变更可控。

但**过度设计文档体系让项目在 V0.1 阶段就背上了沉重的维护负担**。13 个文档/Prompt 文件对一个还没有一行业务代码的项目来说，相当于用 30% 的时间写代码、70% 的时间维护文档。

好的开发模式应该**随项目一起生长**：V0.1 只需要 README + ARCHITECTURE + TASKS + CLAUDE.md 就够了。Code Review Prompt、Refactor Prompt、Release Prompt 这些东西应该在**真正需要它们的时候**再创建。

**一句话建议：停止写文档，开始写接口。**
