# AgentOps Platform — OpenWiki Quickstart

AgentOps Platform is an **enterprise Java AI Agent intelligent development platform** for engineering teams. The first domain agent is the **Business Exception Agent**, which analyzes production exceptions, traces root causes through code and Git history, and will eventually generate safe fix suggestions.

This is not a demo — it is a platform designed for long-term maintenance, incremental expansion, and eventual commercialization.

## Current State

**V0.1 — Platform Foundation stage.** The project has established its Maven multi-module skeleton, documentation system, and core platform contracts. Only two modules have real implementation so far:

| Status | Module | What's Implemented |
|--------|--------|--------------------|
| ✅ Done | `agent-tools` | `ToolRegistry`, `ToolDefinition`, `ToolExecutor`, `ToolResult` |
| ✅ Done | `agent-prompts` | `PromptRegistry`, `PromptTemplate` |
| 🚧 Next | `agent-workflow` | Lightweight Workflow interfaces |
| ⬜ Pending | `agent-memory` | Memory interfaces |
| ⬜ Pending | `agent-runtime` | OpenAI SDK integration, reasoning loop |
| ⬜ Pending | `agent-api` | REST API shell |
| ⬜ Pending | `agent-mcp` | MCP integration surface |
| ⬜ Pending | `business-exception-agent` | Domain diagnosis workflow |

> **Note:** The project has extensive upfront documentation but zero lines of business code as of the mid-V0.1 review. See [`docs/DEVELOPMENT_MODEL_REVIEW.md`](../docs/DEVELOPMENT_MODEL_REVIEW.md) for the critique and pivot plan. The next tasks are all code.

## Where to Go Next

- **[Architecture overview](architecture/overview.md)** — Four-layer architecture, module responsibilities, tech stack, design decisions
- **[Development workflow](development/workflow.md)** — AI-assisted development model, task system, prompt files, environment setup, commit conventions

## Primary Existing Documentation

These root-level files are the canonical reference documents. Read them in this order when entering the project:

| # | File | Purpose |
|---|------|---------|
| 1 | [`README.md`](../README.md) | Project overview and tech stack |
| 2 | [`AGENTS.md`](../AGENTS.md) | AI coding agent rules and module boundaries |
| 3 | [`CLAUDE.md`](../CLAUDE.md) | Claude Code setup and development contract |
| 4 | [`ARCHITECTURE.md`](../ARCHITECTURE.md) | Four-layer architecture definition |
| 5 | [`ROADMAP.md`](../ROADMAP.md) | V0.1–V0.5 roadmap |
| 6 | [`TASKS.md`](../TASKS.md) | Current task list (single source of truth for what's next) |
| 7 | [`CHANGELOG.md`](../CHANGELOG.md) | Completed work log |

### AI Collaboration Prompt Files

These are role-specific prompts for different development scenarios:

| Prompt File | When to Use |
|-------------|-------------|
| [`PROJECT_BOOTSTRAP.md`](../PROJECT_BOOTSTRAP.md) | First entry into the repository |
| [`CONTINUE_DEVELOPMENT.md`](../CONTINUE_DEVELOPMENT.md) | Day-to-day task implementation |
| [`ARCHITECT_PROMPT.md`](../ARCHITECT_PROMPT.md) | Before structural/architecture changes |
| [`CODE_REVIEW_PROMPT.md`](../CODE_REVIEW_PROMPT.md) | Code review sessions |
| [`BUGFIX_PROMPT.md`](../BUGFIX_PROMPT.md) | Defect repair |
| [`REFACTOR_PROMPT.md`](../REFACTOR_PROMPT.md) | Planned refactoring |
| [`RELEASE_PROMPT.md`](../RELEASE_PROMPT.md) | Release preparation |

## Project Structure at a Glance

```
AgentOpsPlatform/
├── pom.xml                          # Maven parent POM (Java 21, Spring Boot 3.5.7)
├── agent-runtime/                   # Model orchestration, reasoning loop, tool calling
├── agent-api/                       # Spring Boot REST API module
├── agent-tools/                     # ✅ Tool Registry contracts (implemented)
├── agent-memory/                    # Memory interfaces (placeholder)
├── agent-workflow/                  # Workflow engine (placeholder)
├── agent-prompts/                   # ✅ Prompt Registry (implemented)
├── agent-mcp/                       # MCP integration (placeholder)
├── business-exception-agent/        # Domain diagnosis agent (placeholder)
├── docs/                            # Supplementary documentation
├── DECISIONS/                       # Architecture Decision Records (empty)
├── AGENTS.md                        # AI coding agent collaboration rules
├── CLAUDE.md                        # Claude Code-specific instructions
├── ARCHITECTURE.md                  # Architecture definition
├── ROADMAP.md                       # Version roadmap
├── TASKS.md                         # Active task list
├── CHANGELOG.md                     # Change log
└── PROJECT_BOOTSTRAP.md             # Full project bootstrap prompt
```

## Quick Start: Building

**Prerequisites:** JDK 21, Maven 3.9+

```powershell
# On Windows with JDK 21 not as default, switch first:
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Verify
mvn -version

# Build all modules
mvn clean compile

# Run tests
mvn test
```

## Key Design Rules

1. **Agents must call tools through the Tool Registry.** Domain agents must never access databases, Git, logs, metrics, or external systems directly.
2. **Prompts live outside Java source code.** Store prompts in `agent-prompts` or documented prompt resources.
3. **One task at a time.** Only the first unfinished task in `TASKS.md` should be worked on.
4. **No heavyweight frameworks.** Use OpenAI Java SDK directly. No LangChain4j, Flowable, or similar unless justified by an architecture decision.
5. **Interface-first boundaries.** All module boundaries (runtime, tools, workflow, memory) are defined as interfaces before implementation.
