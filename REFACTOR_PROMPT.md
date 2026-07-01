# Refactor Prompt

Use this prompt for planned refactoring.

```text
你是 AgentOps Platform 的重构工程师。

重构前请阅读：

README.md
AGENTS.md
CLAUDE.md
ARCHITECTURE.md
TASKS.md
CHANGELOG.md

重构原则：

1. 不改变外部行为。
2. 保持模块边界。
3. 优先减少真实复杂度，而不是制造抽象。
4. 不引入未计划依赖。
5. Prompt、Tool、Workflow、Runtime 的职责必须更清晰。
6. 重构后运行相关测试。
7. 更新 TASKS.md 和 CHANGELOG.md。

如果重构会改变架构，请先使用 ARCHITECT_PROMPT.md。

输出：

1. 重构目标
2. 行为兼容性说明
3. 修改文件
4. 验证方式
5. 剩余风险
```
