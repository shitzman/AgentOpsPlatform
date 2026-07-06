# Architecture Overview

AgentOps Platform uses a **four-layer architecture** with strict separation of concerns. Each layer has clear ownership boundaries enforced by the `AGENTS.md` rules.

## Layer Diagram

```
┌──────────────────────────────────────────┐
│  Enterprise Plugin                       │
│  GitLab, Jira, Feishu, WeCom, Jenkins,   │
│  SkyWalking, Nacos, SonarQube...         │
├──────────────────────────────────────────┤
│  Domain Agent                            │
│  Business Exception Agent — diagnosis    │
│  workflow, prompts, reports, RCA policy  │
├──────────────────────────────────────────┤
│  Tool Layer                              │
│  Log, Git, Code, DB, Redis, K8s,         │
│  Prometheus, Jira tools...               │
├──────────────────────────────────────────┤
│  Agent Runtime                           │
│  Reasoning loop, tool calling, streaming,│
│  memory, permissions, model provider     │
└──────────────────────────────────────────┘
```

## Layer Responsibilities

### 1. Agent Runtime (`agent-runtime`)

The execution engine. Owns model orchestration and runtime mechanics:

- **Reasoning loop** — the core ReAct-style loop: Reason → Tool Call → Observe → Repeat → Final Answer
- **Tool calling** — invokes tools through the `ToolRegistry`, marshals arguments and results
- **Streaming** — supports streaming responses from LLM providers
- **Memory access** — reads/writes execution context through memory abstractions
- **Permission checks** — enforces guardrails before tool execution
- **Model provider boundary** — abstracts the LLM provider (OpenAI Java SDK initially)

The runtime must **not** contain domain-specific diagnosis logic.

### 2. Tool Layer (`agent-tools`)

All external capabilities are represented as tools. Domain agents do not access external systems directly — they call tools through the Tool Registry.

**Implemented interfaces** (in `agent-tools/src/main/java/com/agentops/tools/`):

| Interface | Purpose |
|-----------|---------|
| `ToolDefinition` | Immutable record: `name`, `description`, JSON Schema `parameters` |
| `ToolExecutor` | `@FunctionalInterface` — `ToolResult execute(Map<String, Object> arguments)` |
| `ToolResult` | Record: `success` boolean, `output` string (on success), `error` string (on failure) |
| `ToolRegistry` | Central SPI: `register()`, `unregister()`, `getDefinition()`, `getExecutor()`, `listDefinitions()` |

**Planned tools:**
- Log Tool — query and analyze application logs
- Git Tool — repository search, blame, commit analysis
- Code Tool — source code inspection
- Database Tool — query production databases (read-only)
- Prometheus Tool — metrics and alert queries
- Kafka Tool — message context inspection
- Kubernetes Tool — pod and container inspection

### 3. Domain Agent (`business-exception-agent`)

The Business Exception Agent is the first domain agent. It composes runtime, workflow, prompts, and tools to:

1. Accept a stack trace or exception event
2. Run a diagnosis workflow
3. Query logs, Git history, and code via tools
4. Produce a structured `DiagnosisReport` with root cause, confidence score, related commits, and affected modules
5. (Future) Generate fix suggestions and draft patches

The domain agent owns:
- Diagnosis workflow definition
- Domain-specific prompts
- Diagnosis report schema
- Root cause analysis policy

It does **not** own low-level integrations — those belong in the Tool Layer.

### 4. Enterprise Plugin

Extension points for organizational infrastructure. Plugin-shaped so the platform adapts to different enterprises:

- **IM integrations:** Feishu, WeCom, DingTalk
- **VCS integrations:** GitHub, GitLab
- **CI/CD:** Jenkins
- **Observability:** SkyWalking, Prometheus
- **Configuration:** Nacos
- **Quality:** SonarQube

## Maven Module Map

| Module | Artifact | Group: `com.agentops` | Status |
|--------|----------|-----------------------|--------|
| Root POM | `agentops-platform` | Parent aggregator | Active |
| `agent-runtime` | `agent-runtime` | Runtime engine | Placeholder |
| `agent-api` | `agent-api` | REST API | Placeholder |
| `agent-tools` | `agent-tools` | Tool contracts | ✅ Implemented |
| `agent-memory` | `agent-memory` | Memory abstractions | Placeholder |
| `agent-workflow` | `agent-workflow` | Workflow engine | Placeholder |
| `agent-prompts` | `agent-prompts` | Prompt registry | ✅ Implemented |
| `agent-mcp` | `agent-mcp` | MCP integration | Placeholder |
| `business-exception-agent` | `business-exception-agent` | Domain agent | Placeholder |

All modules inherit from `org.springframework.boot:spring-boot-starter-parent:3.5.7` with Java 21.

## Technology Stack

| Technology | Role |
|------------|------|
| **Java 21** | Language (records, pattern matching, virtual threads) |
| **Spring Boot 3.5.7** | Application framework |
| **Maven** | Build and dependency management |
| **OpenAI Java SDK** | LLM provider abstraction (first integration) |
| **PostgreSQL** | Persistent storage |
| **Redis** | Caching and session state |
| **Kafka** | Event streaming and async messaging |
| **Docker Compose** | Local development infrastructure |
| **OpenTelemetry** | Distributed tracing |
| **Prometheus** | Metrics collection |
| **Grafana** | Metrics visualization |

## Key Design Decisions

1. **No heavyweight agent frameworks.** Use OpenAI Java SDK directly. Avoid LangChain4j, Flowable, or similar unless an architecture decision (stored in `DECISIONS/`) justifies the addition.

2. **Lightweight workflow engine.** Build a custom, purpose-fit workflow engine rather than adopting a BPMN engine. Workflows are deterministic agent step sequences, not general business process automation.

3. **Prompts as resources, not code.** Prompt templates live in `agent-prompts` or classpath resources. Never hard-code prompt strings in Java classes. The `PromptRegistry` supports `{{variable}}` placeholder rendering.

4. **Structured outputs for LLM responses.** Diagnosis reports and tool results use typed records, not free-text strings.

5. **Interface-first module boundaries.** Every module exposes a Java interface (or set of interfaces) defining its contract. Implementations live behind those interfaces.

6. **Constructor injection.** No field injection. Dependencies are explicit in constructors.

7. **Tool Registry as the integration gateway.** No domain agent may bypass the Tool Registry. This is the single most important architectural constraint.

## Source Map

| What | Where |
|------|-------|
| Architecture definition | [`ARCHITECTURE.md`](../../ARCHITECTURE.md) |
| Tool Registry SPI | `agent-tools/src/main/java/com/agentops/tools/ToolRegistry.java` |
| Tool definition record | `agent-tools/src/main/java/com/agentops/tools/ToolDefinition.java` |
| Tool executor interface | `agent-tools/src/main/java/com/agentops/tools/ToolExecutor.java` |
| Tool result record | `agent-tools/src/main/java/com/agentops/tools/ToolResult.java` |
| Prompt Registry SPI | `agent-prompts/src/main/java/com/agentops/prompts/PromptRegistry.java` |
| Prompt template record | `agent-prompts/src/main/java/com/agentops/prompts/PromptTemplate.java` |
| Parent POM | [`pom.xml`](../../pom.xml) |
| Module POMs | `agent-*/pom.xml` |
| Architecture decisions | `DECISIONS/` (currently empty) |
