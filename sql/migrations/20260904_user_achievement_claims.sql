-- 在 liutech 主库执行；兼容旧应用。回滚代码时保留本表与积分流水，防止重复发奖。
CREATE TABLE IF NOT EXISTS user_achievement_claims (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  achievement_code VARCHAR(40) NOT NULL,
  reward_points DECIMAL(12,2) NOT NULL,
  claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_achievement (user_id, achievement_code),
  INDEX idx_user_claimed (user_id, claimed_at),
  CONSTRAINT fk_achievement_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次性成就领取记录';
