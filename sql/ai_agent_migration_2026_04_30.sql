-- 智能看板娘 Agent 迁移脚本
-- 适用于已经初始化过 liutech_ai 的环境。
-- 兼容 MySQL 8.0 官方版本：MySQL 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS。

USE liutech_ai;

SET @schema_name = 'liutech_ai';

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'ai_chat_message'
       AND COLUMN_NAME = 'user_id') = 0,
    'ALTER TABLE ai_chat_message ADD COLUMN user_id VARCHAR(64) NULL COMMENT ''用户ID'' AFTER conversation_id',
    'SELECT ''user_id exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'ai_chat_message'
       AND COLUMN_NAME = 'metadata') = 0,
    'ALTER TABLE ai_chat_message ADD COLUMN metadata JSON NULL COMMENT ''扩展元数据'' AFTER tokens',
    'SELECT ''metadata exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 'ai_chat_message'
       AND COLUMN_NAME = 'updated_at') = 0,
    'ALTER TABLE ai_chat_message ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER created_at',
    'SELECT ''updated_at exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_agent_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(64) NULL,
    conversation_id BIGINT UNSIGNED NULL,
    intent VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input LONGTEXT NOT NULL,
    result_summary TEXT NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at),
    KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent任务表';

CREATE TABLE IF NOT EXISTS ai_agent_action (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id BIGINT UNSIGNED NULL,
    payload JSON NULL,
    result JSON NULL,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent待确认动作表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_call (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    success TINYINT NOT NULL DEFAULT 0,
    input JSON NULL,
    output JSON NULL,
    error_message TEXT NULL,
    duration_ms BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_tool_created (tool_name, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent工具调用记录表';
