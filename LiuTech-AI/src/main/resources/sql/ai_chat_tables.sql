-- AI聊天服务数据库表结构
-- 作者: 刘鑫
-- 时间: 2025-01-31
-- 说明: 用于存储AI聊天会话和消息记录

-- 创建会话表
CREATE TABLE IF NOT EXISTS chat_sessions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
  session_id VARCHAR(64) NOT NULL COMMENT '会话标识符',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID（暂时默认为0）',
  title VARCHAR(255) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  message_count INT NOT NULL DEFAULT 0 COMMENT '消息数量',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  
  UNIQUE KEY uk_user_session (user_id, session_id),
  INDEX idx_user_id (user_id),
  INDEX idx_created_at (created_at),
  INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI聊天会话表';

-- 创建消息表
CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
  session_id VARCHAR(64) NOT NULL COMMENT '会话标识符',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
  role ENUM('user', 'assistant', 'system') NOT NULL COMMENT '消息角色',
  content TEXT NOT NULL COMMENT '消息内容',
  tokens_used INT DEFAULT 0 COMMENT '使用的token数量',
  model_name VARCHAR(100) DEFAULT NULL COMMENT '使用的AI模型名称',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  INDEX idx_session_id (session_id),
  INDEX idx_user_id (user_id),
  INDEX idx_created_at (created_at),
  INDEX idx_role (role),
  
  FOREIGN KEY (session_id, user_id) REFERENCES chat_sessions(session_id, user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI聊天消息表';

-- 创建索引优化查询性能
CREATE INDEX idx_session_messages ON chat_messages(session_id, created_at);
CREATE INDEX idx_user_sessions ON chat_sessions(user_id, updated_at DESC);

-- 插入示例数据（可选）
-- INSERT INTO chat_sessions (session_id, user_id, title) VALUES 
-- ('default', 0, '默认对话'),
-- ('work_session', 0, '工作讨论');