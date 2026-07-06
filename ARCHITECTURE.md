# Architecture

AgentOps Platform uses a four-layer architecture.

## 1. Agent Runtime

The runtime owns model orchestration and execution mechanics.

Responsibilities:

- Reasoning loop.
- Tool calling.
- Workflow coordination.
- Streaming.
- Memory access abstraction.
- Permission checks.
- Model provider boundary.

The runtime must not contain domain-specific diagnosis logic.

## 2. Tool Layer

All external capabilities are represented as tools.

Examples:

- Log Tool
- Git Tool
- Code Tool
- Database Tool (MySQL)
- Redis Tool
- Linux Tool
- Kubernetes Tool
- Prometheus Tool
- Jira Tool

Domain agents must not access external systems directly. They call tools through the Tool Registry.

## 3. Domain Agent

Business Exception Agent is the first domain agent.

Responsibilities:

- Diagnosis workflow.
- Domain prompts.
- Diagnosis report schema.
- Root cause analysis policy.

The domain agent composes runtime, workflow, prompt, and tools. It does not own low-level integrations.

## 4. Enterprise Plugin

Enterprise integrations should be plugin-shaped so the platform can adapt to different organizations.

Examples:

- Feishu
- WeCom
- DingTalk
- GitHub
- GitLab
- Jenkins
- SonarQube
- Nacos
- SkyWalking

## Planned Maven Modules

- `agent-runtime`
- `agent-api`
- `agent-tools`
- `agent-memory`
- `agent-workflow`
- `agent-prompts`
- `agent-mcp`
- `business-exception-agent`

## Key Design Decisions

- Use Java 21 and Spring Boot 3.x.
- Use OpenAI Java SDK directly before introducing any higher-level framework.
- Build a lightweight workflow engine first.
- Keep prompts outside Java source where practical.
- Use structured output for diagnosis results.
