-- AgentOps Platform — H2 开发数据库初始化（MODE=MySQL）
-- 应用启动时自动执行（spring.sql.init.mode=always）
-- 注意：H2 不支持 COMMENT '...' 内联语法，注释已移除

CREATE TABLE IF NOT EXISTS projects (
    id                 VARCHAR(64)  NOT NULL PRIMARY KEY,
    name               VARCHAR(256) NOT NULL,
    description        TEXT,
    git_repo_url       VARCHAR(512),
    git_repo_local_path VARCHAR(512),
    enabled_tools      TEXT         DEFAULT '[]',
    log_source_ids     TEXT         DEFAULT '[]',
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS log_sources (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    project_id  VARCHAR(64)  NOT NULL,
    name        VARCHAR(256) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    properties  TEXT         DEFAULT '{}',
    enabled     BOOLEAN      DEFAULT TRUE,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS conversations (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id  VARCHAR(64)  NOT NULL,
    project_id       VARCHAR(64),
    role             VARCHAR(16)  NOT NULL,
    content          TEXT         NOT NULL,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_conv_id ON conversations(conversation_id);
CREATE INDEX IF NOT EXISTS idx_conv_project ON conversations(project_id);

CREATE TABLE IF NOT EXISTS diagnosis_reports (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY,
    project_id      VARCHAR(64),
    exception_type  VARCHAR(256) NOT NULL,
    summary         TEXT         NOT NULL,
    root_cause      TEXT,
    related_modules TEXT,
    recommendations TEXT,
    confidence      DOUBLE       DEFAULT 0.0,
    severity        VARCHAR(16)  DEFAULT 'medium',
    impact_scope    VARCHAR(64),
    urgency         VARCHAR(32),
    trace_id        VARCHAR(64),
    raw_trace       TEXT,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS memory_entries (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    type        VARCHAR(64)  NOT NULL,
    content     TEXT         NOT NULL,
    metadata    TEXT         DEFAULT '{}',
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mem_type ON memory_entries(type);
