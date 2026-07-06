# Roadmap

## Vision

AgentOps Platform will become an enterprise AI Agent platform for engineering teams.

Business Exception Agent is the first domain agent. Future agents should share the same runtime, workflow, tool, memory, prompt, MCP, permission, and plugin foundations.

## V0.1 Platform Foundation

Goal: establish a maintainable project skeleton and the minimum runtime primitives needed for log-based diagnosis.

- Maven multi-module structure.
- Java 21 and Spring Boot 3.x.
- OpenAI Java SDK integration boundary.
- Tool Registry.
- Prompt Registry.
- Lightweight Workflow Engine.
- Memory interfaces.
- Business Exception Agent MVP workflow.
- Docker Compose infrastructure.
- Baseline documentation and AI development prompts.

## V0.2 Log Diagnosis MVP

Goal: analyze a submitted stack trace and produce a structured diagnosis report.

- Log Tool.
- Stack trace parser.
- Diagnosis workflow.
- Structured output schema.
- Unit tests for tool and workflow behavior.

## V0.3 Repository Intelligence

Goal: connect exceptions to repository context.

- Git Tool.
- Repository search.
- Blame analysis.
- Commit analysis.
- Related module detection.

## V0.4 Observability Context

Goal: support cross-service root cause analysis.

- OpenTelemetry integration.
- Prometheus query tool.
- Trace and metric correlation.

## V0.5 Assisted Fixing

Goal: generate safe fix suggestions and draft patches.

- Patch proposal workflow.
- Reviewer step.
- GitHub and GitLab integration planning.
- Optional PR creation after explicit approval.
