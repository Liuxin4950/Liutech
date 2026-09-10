-- ============================================================
-- AI 模型上下文窗口配置（已有库增量升级）
--
-- 背景：ai_model_config 原本只有 max_tokens（单次输出上限），没有上下文窗口字段，
-- 导致 Input 侧完全没有预算控制：AI 读取长文章或多轮对话后，prompt 会超出模型
-- 上下文上限，表现为「模型卡住」且前端拿不到明确提示。
--
-- 本脚本只做一件事：给 ai_model_config 增加 context_window，并回填已知模型的真实值。
--
-- 执行：
--   mysql -u root -p < Docs/SQL/migration_ai_model_context_window.sql
-- 生产环境执行前请先 mysqldump。
--
-- 幂等：重复执行不会报错（列已存在时自动跳过 ALTER，回填语句可重复执行）。
--
-- 作者：刘鑫
-- ============================================================

-- 库：liutech_ai（AI 服务使用的库）
CREATE DATABASE IF NOT EXISTS liutech_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE liutech_ai;

-- 1. 新增 context_window 列（存在则跳过）
SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_model_config'
    AND COLUMN_NAME = 'context_window'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE ai_model_config ADD COLUMN context_window INT DEFAULT NULL COMMENT ''模型上下文窗口（输入+输出总 token 上限）；输入预算 = 本值 - max_tokens - 安全余量'' AFTER max_tokens',
  'SELECT ''ai_model_config.context_window 已存在，跳过 ALTER'' AS message'
);
PREPARE migrate_stmt FROM @ddl;
EXECUTE migrate_stmt;
DEALLOCATE PREPARE migrate_stmt;

-- 2. 回填已知模型的上下文窗口（数值来源：SiliconFlow 模型页上下文长度）
--    GLM-4.6: 205K   DeepSeek-V3.2: 164K   DeepSeek-R1: 164K   Qwen2.5-7B: 32K
UPDATE ai_model_config SET context_window = 205000 WHERE model_name = 'zai-org/GLM-4.6';
UPDATE ai_model_config SET context_window = 164000 WHERE model_name = 'deepseek-ai/DeepSeek-V3.2';
UPDATE ai_model_config SET context_window = 164000 WHERE model_name = 'deepseek-ai/DeepSeek-R1';
UPDATE ai_model_config SET context_window = 32768  WHERE model_name = 'Qwen/Qwen2.5-7B-Instruct';
UPDATE ai_model_config SET context_window = 128000 WHERE model_name = 'deepseek-ai/DeepSeek-V2.5';

-- 3. 仍然为空的模型（自定义/未收录）不猜测：留 NULL，服务端按
--    spring.ai.security.model-policy-default-context-window 的保守默认值兜底，
--    管理端可随时补填。

-- 4. 校验
SELECT id, model_name, display_name, max_tokens, context_window, temperature, is_enabled, is_default
FROM ai_model_config
ORDER BY sort_order;
