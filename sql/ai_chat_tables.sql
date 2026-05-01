-- AI聊天服务数据库表结构（liutech_ai 库）
-- 作者: 刘鑫
-- 时间: 2025-12-22
-- 版本: v2.0 - 支持多会话管理
--
-- 设计目标:
-- 1) 支持多会话管理：每个用户可以有多个独立会话
-- 2) 完整的会话生命周期管理：创建、重命名、归档、删除
-- 3) 消息统计：实时统计每个会话的消息数量和最后消息时间
-- 4) 扩展字段支持：metadata 使用 JSON，方便后续扩展
-- 5) 索引优化：针对高频查询场景优化索引
--
-- 注意:
-- - 采用 utf8mb4 避免表情等字符存储问题
-- - metadata 使用 JSON，方便后续扩展（例如温度、traceId、错误信息等）
-- - message_count 和 last_message_at 字段用于会话列表快速展示

-- 创建数据库
CREATE DATABASE IF NOT EXISTS liutech_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE liutech_ai;

-- ========================================
-- 1. 会话表 (ai_conversation)
-- ========================================
-- 存储用户会话的基本信息，支持多会话管理
-- message_count: 会话中的消息总数（用户+AI）
-- last_message_at: 最后一条消息的时间，用于会话排序
-- 简化设计：移除不必要的 type 和 metadata 字段
CREATE TABLE IF NOT EXISTS ai_conversation
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID，主键',
    user_id         VARCHAR(64)     NOT NULL COMMENT '用户ID',
    title           VARCHAR(200)    NULL COMMENT '会话标题（可重命名）',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '会话状态：0=正常, 9=已归档',
    message_count   INT             NOT NULL DEFAULT 0 COMMENT '会话中的消息总数',
    last_message_at DATETIME        NULL COMMENT '最后消息时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_last (user_id, last_message_at) COMMENT '按用户和最后消息时间查询',
    KEY idx_user_status (user_id, status) COMMENT '按用户和状态查询'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI会话表';

-- ========================================
-- 2. 消息表 (ai_chat_message)
-- ========================================
-- 存储会话中的具体消息内容
-- conversation_id: 关联到 ai_conversation 表
-- role: 消息角色（user=用户消息, assistant=AI回复, system=系统消息）
-- seq_no: 消息序号，用于会话内消息排序
-- status: 消息状态（1=完成, 2=部分, 9=错误）
CREATE TABLE IF NOT EXISTS ai_chat_message
(
    id              BIGINT UNSIGNED                    NOT NULL AUTO_INCREMENT COMMENT '消息ID，主键',
    conversation_id BIGINT UNSIGNED                    NOT NULL COMMENT '会话ID（外键关联 ai_conversation.id）',
    user_id         VARCHAR(64)                        NOT NULL COMMENT '用户ID',
    role            ENUM ('user','assistant','system') NOT NULL COMMENT '消息角色：user=用户, assistant=AI, system=系统',
    content         LONGTEXT                           NULL COMMENT '消息内容',
    seq_no          INT                                NOT NULL DEFAULT 0 COMMENT '消息序号（会话内排序）',
    model           VARCHAR(100)                       NULL COMMENT '使用的AI模型名',
    tokens          INT                                NULL COMMENT 'Token使用量估算',
    metadata        JSON                               NULL COMMENT '扩展元数据（JSON格式）',
    status          TINYINT                            NOT NULL DEFAULT 1 COMMENT '消息状态：1=完成, 2=部分, 9=错误',
    created_at      DATETIME                           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME                           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at) COMMENT '按用户和时间查询历史',
    KEY idx_user_role (user_id, role) COMMENT '按用户和角色查询',
    KEY idx_conv_created (conversation_id, created_at) COMMENT '按会话查询消息',
    KEY idx_conv_seq (conversation_id, seq_no) COMMENT '按会话和序号查询',
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI聊天消息表';

-- ========================================
-- 3. 用户记忆摘要表 (ai_chat_memory_summary)
-- ========================================
-- 可选：用户级记忆摘要，用于长对话场景的上下文压缩
-- 当对话历史很长时，可以将早期对话摘要存储在此表中
CREATE TABLE IF NOT EXISTS ai_chat_memory_summary
(
    user_id        VARCHAR(64) NOT NULL COMMENT '用户ID',
    summary        LONGTEXT    NOT NULL COMMENT '记忆摘要内容',
    token_estimate INT         NULL COMMENT '摘要的token估算',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户级记忆摘要表（可选）';


-- 创建AI模型配置表
CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称（如 zai-org/GLM-4.6）',
  `display_name` VARCHAR(100) NOT NULL COMMENT '显示名称（如 GLM-4.6）',
  `provider` VARCHAR(50) NOT NULL DEFAULT 'siliconflow' COMMENT '提供商（siliconflow/openai/ollama等）',
  `is_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用（0=禁用 1=启用）',
  `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否为默认模型（0=否 1=是，只能有一个默认）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序（数字越小越靠前）',
  `max_tokens` INT DEFAULT NULL COMMENT '最大token数限制',
  `temperature` DECIMAL(3,2) DEFAULT 0.90 COMMENT '默认温度参数',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '模型描述',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_name` (`model_name`) COMMENT '模型名称唯一索引',
  KEY `idx_enabled_sort` (`is_enabled`, `sort_order`) COMMENT '启用状态和排序索引',
  KEY `idx_default` (`is_default`) COMMENT '默认模型索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';

-- 初始化默认数据
-- 注意：zai-org/GLM-4.6 设置为默认模型（is_default=1）
INSERT INTO `ai_model_config` (`model_name`, `display_name`, `provider`, `is_enabled`, `is_default`, `sort_order`, `max_tokens`, `temperature`, `description`) VALUES
('zai-org/GLM-4.6', 'GLM-4.6', 'siliconflow', 1, 1, 1, 8192, 0.90, '智谱AI GLM-4.6模型，通用对话能力强（默认）'),
('Qwen/Qwen2.5-7B-Instruct', 'Qwen2.5-7B', 'siliconflow', 1, 0, 2, 8192, 0.90, '阿里通义千问2.5-7B指令模型'),
('deepseek-ai/DeepSeek-V2.5', 'DeepSeek-V2.5', 'siliconflow', 0, 0, 3, 4096, 0.70, '深度求索V2.5模型，推理能力强')
ON DUPLICATE KEY UPDATE
  `display_name` = VALUES(`display_name`),
  `provider` = VALUES(`provider`),
  `is_enabled` = VALUES(`is_enabled`),
  `is_default` = VALUES(`is_default`),
  `sort_order` = VALUES(`sort_order`),
  `max_tokens` = VALUES(`max_tokens`),
  `temperature` = VALUES(`temperature`),
  `description` = VALUES(`description`);

-- ========================================
-- 5. AI Agent 任务与动作审计表
-- ========================================
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
