-- 对齐当前初始化快照的验证码查找索引，不删除旧环境已有的过期清理索引。
SET @liutech_verification_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema=DATABASE() AND table_name='verification_codes' AND index_name='idx_email_type_used_exp'),
    'SELECT 1',
    'ALTER TABLE verification_codes ADD INDEX idx_email_type_used_exp (email, type, used, expires_at)'
);
PREPARE liutech_verification_stmt FROM @liutech_verification_sql;
EXECUTE liutech_verification_stmt;
DEALLOCATE PREPARE liutech_verification_stmt;

SET @liutech_verification_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema=DATABASE() AND table_name='verification_codes' AND index_name='idx_created_at'),
    'SELECT 1',
    'ALTER TABLE verification_codes ADD INDEX idx_created_at (created_at)'
);
PREPARE liutech_verification_stmt FROM @liutech_verification_sql;
EXECUTE liutech_verification_stmt;
DEALLOCATE PREPARE liutech_verification_stmt;
SET @liutech_verification_sql = NULL;
