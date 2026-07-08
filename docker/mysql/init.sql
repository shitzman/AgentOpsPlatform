-- AgentOps Platform — MySQL 初始化脚本
-- 容器首次启动时自动执行

-- 确保使用 utf8mb4 字符集
ALTER DATABASE agentops CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ========================================================================
-- V1 新表
-- ========================================================================

-- 项目配置表
CREATE TABLE IF NOT EXISTS projects (
    id                 VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '唯一标识（UUID）',
    name               VARCHAR(256) NOT NULL COMMENT '项目名称',
    description        TEXT         COMMENT '项目描述',
    git_repo_url       VARCHAR(512) COMMENT '远程 Git 仓库地址',
    git_repo_local_path VARCHAR(512) COMMENT '本地 Git 仓库路径',
    enabled_tools      JSON         COMMENT '启用的工具名列表（JSON 数组）',
    log_source_ids     JSON         COMMENT '关联的日志源 ID 列表（JSON 数组）',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    INDEX idx_project_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检测项目配置表';

-- 日志源配置表
CREATE TABLE IF NOT EXISTS log_sources (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '唯一标识（UUID）',
    project_id  VARCHAR(64)  NOT NULL COMMENT '所属项目 ID',
    name        VARCHAR(256) NOT NULL COMMENT '显示名称（如"生产环境 ES"）',
    type        VARCHAR(32)  NOT NULL COMMENT '日志源类型：TEXT_INPUT / FILE_PATH / ELASTICSEARCH',
    properties  JSON         COMMENT '类型相关的连接参数（JSON 对象）',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_ls_project (project_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日志源配置表';

-- 对话历史表
CREATE TABLE IF NOT EXISTS conversations (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    conversation_id  VARCHAR(64)  NOT NULL COMMENT '对话会话 ID（UUID）',
    project_id       VARCHAR(64)  COMMENT '关联项目 ID（可选）',
    role             VARCHAR(16)  NOT NULL COMMENT '消息角色：system / user / assistant',
    content          MEDIUMTEXT   NOT NULL COMMENT '消息内容',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_conv_conversation (conversation_id),
    INDEX idx_conv_project (project_id),
    INDEX idx_conv_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话历史表';

-- ========================================================================
-- V0.x 已有表（保持兼容）
-- ========================================================================

-- Memory 存储表（对应 MemoryStore 接口）
CREATE TABLE IF NOT EXISTS memory_entries (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '唯一标识',
    type        VARCHAR(64)  NOT NULL COMMENT '记忆类型：conversation/diagnosis/tool_result',
    content     MEDIUMTEXT   NOT NULL COMMENT '记忆内容',
    metadata    JSON         COMMENT '附加元数据（JSON 格式）',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_type (type),
    INDEX idx_created_at (created_at),
    FULLTEXT INDEX idx_content (content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent Memory 存储表';

-- 诊断报告表
CREATE TABLE IF NOT EXISTS diagnosis_reports (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '报告唯一标识',
    project_id      VARCHAR(64)  COMMENT '关联项目 ID（V1 新增）',
    exception_type  VARCHAR(256) NOT NULL COMMENT '异常类型全名',
    summary         TEXT         NOT NULL COMMENT '异常摘要',
    root_cause      TEXT         COMMENT '根因分析',
    related_modules JSON         COMMENT '关联模块列表',
    recommendations JSON         COMMENT '修复建议列表',
    confidence      DOUBLE       NOT NULL DEFAULT 0.0 COMMENT '诊断置信度 (0-1)',
    severity        VARCHAR(16)  DEFAULT 'medium' COMMENT '严重级别：critical/high/medium/low（V1 新增）',
    impact_scope    VARCHAR(64)  COMMENT '影响范围（V1 新增）',
    urgency         VARCHAR(32)  COMMENT '紧急程度（V1 新增）',
    trace_id        VARCHAR(64)  COMMENT 'OpenTelemetry Trace ID（V1 新增）',
    raw_trace       MEDIUMTEXT   COMMENT '原始堆栈文本',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_project (project_id),
    INDEX idx_exception_type (exception_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='诊断报告表';
