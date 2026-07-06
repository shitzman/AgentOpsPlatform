# CLAUDE.md

Claude Code should treat this repository as a long-running enterprise Java AI Agent platform, not as a throwaway sample.

## Start Here

Read these files before editing:

1. `README.md`
2. `AGENTS.md`
3. `ARCHITECTURE.md`
4. `ROADMAP.md`
5. `TASKS.md`
6. `CHANGELOG.md`

For first-time bootstrapping, use `PROJECT_BOOTSTRAP.md`.

For normal development, use `CONTINUE_DEVELOPMENT.md`.

## Development Contract

- Work on one task at a time.
- Prefer minimal, production-shaped changes.
- Keep module boundaries explicit.
- Add tests with new tools and workflow behavior.
- Update project documents with code changes.
- Explain tradeoffs when adding dependencies.

## Current Priority

V0.1 focuses on platform foundations:

- Maven multi-module project structure.
- Spring Boot API shell.
- OpenAI Java SDK integration boundary.
- Tool Registry.
- Prompt Registry.
- Lightweight Workflow Engine.
- Memory interfaces.
- Docker Compose for PostgreSQL, Redis, Kafka, Prometheus, Grafana, and OpenTelemetry.

Do not implement automatic patch generation, automatic pull requests, a knowledge base, or a chat bot in V0.1.

## Environment Setup

This project requires **JDK 21**. The machine has JDK 21 installed at `C:\Program Files\Java\jdk-21`, but the global `JAVA_HOME` points to JDK 17.

Before running any Maven commands, switch to JDK 21 in the current PowerShell session:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

This is a temporary, per-session switch. It does not affect other projects or system-wide settings.

Verify the switch worked:

```powershell
mvn -version
# Should show: Java version: 21.0.6, vendor: Oracle Corporation
```

## OpenWiki

This repository has documentation located in the /openwiki directory.

Start here:
- [OpenWiki quickstart](openwiki/quickstart.md)

OpenWiki includes repository overview, architecture notes, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

When working in this repository, read the OpenWiki quickstart first, then follow its links to the relevant architecture, workflow, domain, operation, and testing notes.
