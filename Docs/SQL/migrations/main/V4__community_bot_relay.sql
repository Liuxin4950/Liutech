-- 独立控制 AI 自动接力；默认 TRUE 保留升级前行为，不重置其它配置或历史任务。
SET @liutech_relay_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name='community_settings' AND column_name='bot_relay_enabled'),
    'SELECT 1',
    'ALTER TABLE community_settings ADD COLUMN bot_relay_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER enabled'
);
PREPARE liutech_relay_stmt FROM @liutech_relay_sql;
EXECUTE liutech_relay_stmt;
DEALLOCATE PREPARE liutech_relay_stmt;
SET @liutech_relay_sql = NULL;
