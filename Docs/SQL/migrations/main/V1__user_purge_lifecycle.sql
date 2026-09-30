CREATE TABLE IF NOT EXISTS user_purge_tasks (
  user_id BIGINT NOT NULL PRIMARY KEY COMMENT '已删除用户ID，无外键',
  attempts INT UNSIGNED NOT NULL DEFAULT 0,
  next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  lease_token VARCHAR(36) DEFAULT NULL,
  last_error VARCHAR(100) DEFAULT NULL,
  completed_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_purge_due (completed_at, next_attempt_at, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨服务用户清理任务';
