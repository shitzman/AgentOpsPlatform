# OpenWiki Init Plan

## Gap Analysis

Last update was at git head `86f990c` (V0.5). Current HEAD is `cc9ad06`. Since then:

**Committed changes (86f990c → cc9ad06):**
- V1.0 Phase 1: MySQL + MyBatis-Plus + agent-repository module (entities, mappers, MySqlMemoryStore, MySqlProjectManager)
- V1.0 Phase 2: EnvironmentCollector, GitContextProvider, LogExtractor, DiagnosisContext
- V1.0 Phase 3: Multi-source diagnosis (enhanced system prompt, context injection, report persistence, history query API)
- Frontend V1.0 upgrade: diagnosis history tab, multi-source context input, enhanced report display
- H2 schema fixes, frontend rendering fixes, date serialization fixes
- Log-only mode (stack-trace-free log analysis, new log-analysis-system.txt prompt)

**Uncommitted changes (working tree):**
- ES real integration (ElasticsearchLogProvider rewritten from mock to real HttpClient-based _search API)
- File upload for log sources (multipart, LogFileStorage)
- Test connection endpoint (log source connectivity validation)
- DTO/VO refactoring: Controllers thinned to ~135-215 lines; DiagnosisService, ProjectService, ConversationService, etc. extracted
- Tool call loop (human-in-the-loop): ToolCallSessionStore, POST /api/chat/continue, pending tool approval UI
- Project workbench tab (new workflow entry point)
- Log fetch endpoint (POST /api/projects/{id}/logsources/{lsId}/fetch)
- Agent tools tests: ElasticsearchLogProviderTest (15 tests)

## Pages to Create/Update

### 1. openwiki/quickstart.md (REWRITE)
- Update status to V1.0
- New module table including agent-repository
- New tech stack: H2 for dev, MySQL for production, MyBatis-Plus
- New diagram showing service layer
- New features: tool approval loop, workbench, log-only mode, file upload, ES real integration
- Links to all sections

### 2. openwiki/architecture/overview.md (REWRITE)
- Update for V1.0: agent-repository layer (MySQL + MyBatis-Plus)
- Service layer (DiagnosisService, ProjectService, etc.)
- DTO/VO architecture
- Tool call loop architecture (ToolCallSessionStore)
- Updated dependency graph with repository module

### 3. openwiki/api/rest-api.md (REWRITE)
- All new endpoints: diagnosis history, chat/continue, log fetch, context snapshot, log source test, file upload
- DTO/VO structure and conventions
- Service layer responsibilities

### 4. openwiki/domain/business-exception.md (UPDATE)
- DiagnosisContext model
- Log-only mode and log-analysis-system prompt
- Multi-source context injection
- Tool call loop (human-in-the-loop)
- ProjectManager → MySqlProjectManager migration note

### 5. openwiki/development/workflow.md (UPDATE)
- Updated test coverage (44 tests from 24)
- New test patterns: ElastisearchLogProviderTest, LogFileStorageTest
- Tools: ES integration, file upload, test connection

## Source Evidence
- /CHANGELOG.md - V1.0 change log
- /TASKS.md - task lists for V1.0 features
- /git log --oneline - shows exact sequence
- /agent-api/src/main/java/com/agentops/api/service/ - all services
- /agent-api/src/main/java/com/agentops/api/dto/ - all DTOs
- /agent-api/src/main/java/com/agentops/api/vo/ - all VOs
- /agent-repository/ - persistence layer
- /agent-tools/ - ES, FileLog, LogExtractor, context collectors

## Remaining Questions
- Exact test count: reported as 44 in TASKS.md - verify
- Untracked test dir at /agent-api/src/test/java/com/ - likely empty (just .gitkeep subdir)
