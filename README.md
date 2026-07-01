# AgentOps Platform

AgentOps Platform is an enterprise intelligent engineering platform for Java AI Agents.

The first domain agent is the Business Exception Agent: an agent that helps engineering teams analyze production business exceptions, identify likely root causes, connect symptoms to code and commits, and eventually produce safe fix suggestions.

This repository is intentionally being built as a long-term platform, not as a demo.

## Current Status

The project is in V0.1 bootstrap.

The current milestone focuses on documentation, project structure, and core platform contracts before implementing domain features.

## Architecture

The platform is organized around four layers:

- Agent Runtime: reasoning loop, tool calling, workflow, streaming, memory, permissions.
- Tool Layer: all external capabilities are exposed as tools.
- Domain Agent: Business Exception Agent owns diagnosis workflow, prompts, and reports.
- Enterprise Plugin: organization-specific integrations such as GitLab, Jira, Feishu, WeCom, Jenkins, and SkyWalking.

See `ARCHITECTURE.md` for details.

## Planned Modules

- `agent-runtime`
- `agent-api`
- `agent-tools`
- `agent-memory`
- `agent-workflow`
- `agent-prompts`
- `agent-mcp`
- `business-exception-agent`

## Technology Direction

- Java 21
- Spring Boot 3.x
- Maven multi-module
- OpenAI Java SDK
- PostgreSQL
- Redis
- Kafka
- Docker Compose
- OpenTelemetry
- Prometheus
- Grafana

## AI Development Workflow

Use these files to keep AI-assisted development consistent:

- `PROJECT_BOOTSTRAP.md`: first-time project bootstrap prompt.
- `CONTINUE_DEVELOPMENT.md`: normal task execution prompt.
- `ARCHITECT_PROMPT.md`: review before architecture changes.
- `CODE_REVIEW_PROMPT.md`: review code changes.
- `BUGFIX_PROMPT.md`: fix defects.
- `REFACTOR_PROMPT.md`: perform planned refactors.
- `RELEASE_PROMPT.md`: prepare releases.
- `AGENTS.md`: repository rules for AI coding agents.
- `CLAUDE.md`: Claude Code-specific working guide.

## Next Step

Complete the first unfinished item in `TASKS.md`: create the Maven multi-module project skeleton.
