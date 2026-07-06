# Development Workflow

## AI Collaboration Model

This repository is designed for **AI-assisted, single-task-at-a-time development**. The model uses a set of prompt files that define specific AI roles for different development scenarios.

### The Task Loop

```
1. Read governance docs (README, AGENTS, ARCHITECTURE, ROADMAP, TASKS)
2. Find first unchecked task in TASKS.md
3. Implement only that task
4. Update TASKS.md ✅
5. Update CHANGELOG.md
6. If architecture changed → update ARCHITECTURE.md
7. Commit with semantic message
8. Push to origin + github-origin
```

This loop prevents scope creep and keeps changes small and reviewable.

### Prompt Files

Each prompt file casts the AI into a specific role. The human selects the right prompt for the current phase.

| File | When to Use |
|------|-------------|
| `PROJECT_BOOTSTRAP.md` | First time an AI agent enters the repository |
| `CONTINUE_DEVELOPMENT.md` | Normal task-by-task implementation |
| `ARCHITECT_PROMPT.md` | Before structural/architectural changes |
| `CODE_REVIEW_PROMPT.md` | Reviewing completed code |
| `BUGFIX_PROMPT.md` | Fixing a defect |
| `REFACTOR_PROMPT.md` | Planned refactoring |
| `RELEASE_PROMPT.md` | Release preparation |

**Important:** The prompt mechanism is currently manual — a human copies the prompt text and pastes it to the AI tool. Only `CLAUDE.md` is auto-loaded by Claude Code.

### AGENTS.md vs CLAUDE.md

- **`AGENTS.md`** — Rules for all AI coding agents (Codex, Cursor, Claude Code). Defines module boundaries, coding preferences, prohibited patterns, commit conventions.
- **`CLAUDE.md`** — Claude Code-specific instructions. Auto-loaded by Claude Code. Includes environment setup for JDK 21.

Both files should be kept in sync for their overlapping content.

## Environment Setup

**JDK 21 is required.** The machine may have a different default JDK (e.g., JDK 17). Switch before building:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Verify:
```bash
mvn -version
# Expected: Java version: 21.0.6, vendor: Oracle Corporation
```

Build:
```bash
mvn clean compile      # compile all modules
mvn clean test         # run tests
mvn clean install      # full build and install to local repo
```

## Coding Conventions

### Required Patterns
- **Java 21** with modern features (records, sealed types, pattern matching where appropriate)
- **Constructor injection** over field injection
- **Records** for immutable data transfer objects
- **Interface-first boundaries** — every module exposes contracts via interfaces
- **Structured outputs** for LLM responses
- **Unit tests** for tools and core workflow behavior

### Prohibited Patterns
- God services (single class doing too much)
- Large controllers
- Static utility sprawl
- Hidden external system access from domain agents
- Prompt strings scattered across Java classes
- Unplanned dependency additions

### Commit Convention

```
feat: add <thing>
fix: fix <thing>
refactor: refactor <thing>
docs: update <thing>
test: add tests for <thing>
```

Commit after each completed task. Do not batch unrelated changes.

## Git Remotes

| Remote | URL | Purpose |
|--------|-----|---------|
| `origin` | `https://gitee.com/shitzman/agent-ops-platform.git` | Primary (Gitee) |
| `github-origin` | `https://github.com/shitzman/AgentOpsPlatform.git` | Mirror (GitHub) |

Push to both after completing a task:
```bash
git push origin master
git push github-origin master
```

## Current Milestone: V0.1 Platform Foundation

The focus is on establishing platform infrastructure, not business logic.

**Completed:**
- [x] AI development prompt assets
- [x] Maven multi-module project skeleton
- [x] Java 21 + Spring Boot 3.5.7 parent configuration
- [x] Module placeholders for all 8 modules
- [x] Tool Registry interfaces (`ToolDefinition`, `ToolExecutor`, `ToolResult`, `ToolRegistry`)
- [x] Prompt Registry interfaces (`PromptTemplate`, `PromptRegistry`)

**Next tasks (in order):**
1. Define lightweight Workflow interfaces
2. Define Memory interfaces
3. Add OpenAI Java SDK integration boundary
4. Add Docker Compose baseline (PostgreSQL, Redis, Kafka, OpenTelemetry, Prometheus, Grafana)
5. Add first Business Exception Agent log diagnosis workflow

See [TASKS.md](../../TASKS.md) for the authoritative live list.

## Known Pain Points

From the [mid-V0.1 development model review](../../docs/DEVELOPMENT_MODEL_REVIEW.md):

1. **Documentation over-production** — 13 doc/prompt files exist but 0 business code. The review recommends freezing new documentation and focusing on code for the next 3 tasks.
2. **Manual prompt mechanism** — The prompt system requires humans to copy-paste text. Only `CLAUDE.md` has auto-load support.
3. **Stale TASKS.md** — Prompt Registry interfaces exist in code but remain unchecked in TASKS.md. Verify task status against actual code before starting.

## Testing Strategy

The project expects:
- Unit tests for new tools (when a tool implementation is added)
- Unit tests for core workflow behavior
- Tests should be in `src/test/java` under the relevant module
- No integration or E2E tests defined yet (Docker Compose infrastructure pending)

## When Changing Code

- **Adding a new module interface**: Update `ARCHITECTURE.md`, `AGENTS.md` boundary descriptions, and module `pom.xml`
- **Adding a domain agent**: Create it under its own module, keep it thin (compose primitives), don't add direct external access
- **Adding a tool**: Implement `ToolExecutor`, register in `ToolRegistry`, add unit tests
- **Adding a prompt**: Store in `agent-prompts` resources, register in `PromptRegistry`
- **Changing architecture**: Run the `ARCHITECT_PROMPT.md` review first, update `ARCHITECTURE.md`, record decision in `DECISIONS/`
