-- AgentOps Platform — MySQL 初始化脚本
-- 容器首次启动时自动执行

-- 确保使用 utf8mb4 字符集
ALTER DATABASE agentops CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

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
    exception_type  VARCHAR(256) NOT NULL COMMENT '异常类型全名',
    summary         TEXT         NOT NULL COMMENT '异常摘要',
    root_cause      TEXT         COMMENT '根因分析',
    related_modules JSON         COMMENT '关联模块列表',
    recommendations JSON         COMMENT '修复建议列表',
    confidence      DOUBLE       NOT NULL DEFAULT 0.0 COMMENT '诊断置信度 (0-1)',
    raw_trace       MEDIUMTEXT   COMMENT '原始堆栈文本',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_exception_type (exception_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='诊断报告表';
