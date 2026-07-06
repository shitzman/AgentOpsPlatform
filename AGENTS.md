# AGENTS.md

This file defines how AI coding agents should work in the AgentOps Platform repository.

## Project Identity

AgentOps Platform is an enterprise Java AI Agent platform. The first domain agent is the Business Exception Agent.

The goal is not a demo. The goal is a maintainable, extensible platform for enterprise engineering agents.

## Working Rules

- Read `README.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `TASKS.md`, and `CHANGELOG.md` before implementation.
- Complete only the first unfinished task in `TASKS.md` unless the user explicitly asks otherwise.
- Keep changes small and reviewable.
- Update `TASKS.md` and `CHANGELOG.md` after each completed task.
- Update `ARCHITECTURE.md` when module boundaries, dependencies, or runtime behavior change.
- Do not introduce heavyweight agent frameworks unless an architecture decision records the reason.
- Do not hard-code large prompts in Java code. Store prompts in `agent-prompts` or documented prompt resources.

## Architecture Boundaries

- `agent-runtime`: model orchestration, reasoning loop, tool calling, streaming, permission hooks.
- `agent-tools`: tool contracts and tool implementations.
- `agent-workflow`: lightweight workflow primitives.
- `agent-memory`: memory interfaces and persistence adapters.
- `agent-prompts`: prompt registry and prompt assets.
- `agent-mcp`: MCP integration surface.
- `business-exception-agent`: domain workflow, diagnosis prompts, diagnosis reports.
- `agent-api`: Spring Boot API and delivery layer.

Agents must call tools through the Tool Registry. They must not bypass tools to access databases, Git repositories, logs, metrics, or external systems.

## Coding Preferences

- Java 21.
- Spring Boot 3.x.
- Maven multi-module project.
- Constructor injection.
- Records for immutable data transfer where appropriate.
- Interface-first boundaries for runtime, tools, workflow, and memory.
- Structured outputs for LLM responses.
- Unit tests for new tools and core workflow behavior.

## Prohibited Patterns

- God services.
- Large controllers.
- Static utility sprawl.
- Hidden external system access from domain agents.
- Prompt strings scattered across Java classes.
- Unplanned dependency additions.

## Git Remotes

The repository has two remotes:

| Remote | URL | Purpose |
|--------|-----|---------|
| `origin` | `https://gitee.com/shitzman/agent-ops-platform.git` | 主仓库（Gitee） |
| `github-origin` | `https://github.com/shitzman/AgentOpsPlatform.git` | 镜像仓库（GitHub） |

Default push target is `origin` (Gitee). Push to both when a task is complete:

```bash
git push origin master
git push github-origin master
```

## Commit Convention

Commits must be semantic:

- `feat:` — new feature
- `fix:` — bug fix
- `refactor:` — code refactoring
- `docs:` — documentation update
- `test:` — test addition or update

Example:

```
feat: add Tool Registry interface

- Define Tool contract with ToolDefinition and ToolExecutor
- Add ToolRegistry SPI
- Add package-info.java for agent-tools module

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

Commit after each completed task. Do not batch unrelated changes into one commit.

## Recommended Prompt Files

- `PROJECT_BOOTSTRAP.md`: first repository bootstrap.
- `CONTINUE_DEVELOPMENT.md`: normal task implementation.
- `ARCHITECT_PROMPT.md`: architecture review before structural changes.
- `CODE_REVIEW_PROMPT.md`: code review.
- `BUGFIX_PROMPT.md`: defect repair.
- `REFACTOR_PROMPT.md`: planned refactoring.
- `RELEASE_PROMPT.md`: release preparation.

## OpenWiki

This repository has documentation located in the /openwiki directory.

Start here:
- [OpenWiki quickstart](openwiki/quickstart.md)

OpenWiki includes repository overview, architecture notes, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

When working in this repository, read the OpenWiki quickstart first, then follow its links to the relevant architecture, workflow, domain, operation, and testing notes.
