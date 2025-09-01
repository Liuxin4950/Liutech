-- LiuTech AI 数据库表结构
-- 作者: 刘鑫
-- 时间: 2025-01-31

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `liutech_ai` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE `liutech_ai`;

-- 创建会话表
CREATE TABLE IF NOT EXISTS `chat_sessions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID（主键）',
    `user_id` VARCHAR(255) NOT NULL COMMENT '用户ID',
    `session_id` VARCHAR(255) NOT NULL COMMENT '会话标识符',
    `title` VARCHAR(500) DEFAULT '新对话' COMMENT '会话标题',
    `message_count` INT DEFAULT 0 COMMENT '消息数量',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at` TIMESTAMP NULL DEFAULT NULL COMMENT '删除时间（软删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_session` (`user_id`, `session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI聊天会话表';

-- 创建消息表
CREATE TABLE IF NOT EXISTS `chat_messages` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID（主键）',
    `session_id` VARCHAR(255) NOT NULL COMMENT '会话标识符',
    `user_id` VARCHAR(255) NOT NULL COMMENT '用户ID',
    `role` ENUM('user', 'assistant', 'system') NOT NULL COMMENT '消息角色',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `tokens_used` INT DEFAULT 0 COMMENT '使用的token数量',
    `model_name` VARCHAR(100) DEFAULT NULL COMMENT '使用的AI模型名称',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role` (`role`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_session_user` (`session_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI聊天消息表';

-- 插入示例数据（可选）
-- INSERT INTO `chat_sessions` (`user_id`, `session_id`, `title`, `message_count`) VALUES
-- ('test_user', 'default', '测试会话', 0);

-- 查看表结构
-- DESCRIBE `chat_sessions`;
-- DESCRIBE `chat_messages`;

-- 查看表数据
-- SELECT * FROM `chat_sessions`;
-- SELECT * FROM `chat_messages`;