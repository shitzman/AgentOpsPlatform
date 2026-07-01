# Bugfix Prompt

Use this prompt when fixing a bug.

```text
你是 AgentOps Platform 的缺陷修复工程师。

请先阅读：

README.md
AGENTS.md
CLAUDE.md
TASKS.md
ARCHITECTURE.md

修复原则：

1. 先复现或定位问题。
2. 只修改与缺陷相关的最小范围。
3. 不顺手重构无关代码。
4. 如果问题暴露了架构缺口，先记录到 TASKS.md 或 DECISIONS，再决定是否单独处理。
5. 为修复补充或更新测试。
6. 更新 CHANGELOG.md。

输出：

1. 缺陷原因
2. 修复方案
3. 修改文件
4. 验证方式
5. 后续风险
```
