-- AI模型配置表
-- 作者: 刘鑫
-- 时间: 2025-01-18
-- 用途: 存储可用的AI模型配置，供管理员管理和设置默认模型

-- 使用 liutech_ai 数据库
USE liutech_ai;

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
