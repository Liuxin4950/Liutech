-- 已有重复 seq_no 时 ADD UNIQUE 会失败，必须先核验并修复存量，不能自动丢弃消息。
-- 初始化快照已带唯一索引，因此新库接入 Flyway 时允许该步骤为空操作。
SET @liutech_seq_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'ai_chat_message' AND index_name = 'uk_conv_seq'),
    'SELECT 1',
    'ALTER TABLE ai_chat_message ADD UNIQUE INDEX uk_conv_seq (conversation_id, seq_no)'
);
PREPARE liutech_seq_stmt FROM @liutech_seq_sql;
EXECUTE liutech_seq_stmt;
DEALLOCATE PREPARE liutech_seq_stmt;

SET @liutech_seq_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'ai_chat_message' AND index_name = 'idx_conv_seq'),
    'ALTER TABLE ai_chat_message DROP INDEX idx_conv_seq',
    'SELECT 1'
);
PREPARE liutech_seq_stmt FROM @liutech_seq_sql;
EXECUTE liutech_seq_stmt;
DEALLOCATE PREPARE liutech_seq_stmt;
