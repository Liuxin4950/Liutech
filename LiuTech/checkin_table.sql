-- 用户签到记录表
CREATE TABLE IF NOT EXISTS user_checkins (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '签到记录ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  checkin_date DATE NOT NULL COMMENT '签到日期',
  points_earned DECIMAL(12,2) NOT NULL DEFAULT 1.00 COMMENT '获得积分',
  consecutive_days INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '连续签到天数',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user_date (user_id, checkin_date) COMMENT '用户日期唯一索引',
  INDEX idx_user_id (user_id),
  INDEX idx_checkin_date (checkin_date),
  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户签到记录表';