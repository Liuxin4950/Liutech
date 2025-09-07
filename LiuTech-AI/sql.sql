-- AI聊天服务数据库表结构（liutech_ai 库）
-- 作者: 刘鑫
-- 时间: 2025-09-05
-- 设计目标:
-- 1) 支持“按用户聚合”的最小记忆：不分会话/分区，单一 user_id 即一个记忆域
-- 2) 存储每轮的用户消息与AI消息，便于按时间线取回最近N条（默认20条）构造上下文
-- 3) 预留扩展字段（model/tokens/metadata/status），便于后续做配额统计、调试追踪、错误标记
-- 4) 提供可选的摘要表，用于后续做滚动摘要（RAG/长对话优化），当前可以不启用

-- 注意:
-- - 采用 utf8mb4 避免表情等字符存储问题
-- - metadata 使用 JSON，方便后续扩展（例如温度、traceId、错误信息等）。也可以先当作字符串写入有效JSON

CREATE DATABASE IF NOT EXISTS liutech_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE liutech_ai;

-- 1) 必选：消息明细表（按用户时间线组织）
-- 设计说明：
-- - 不区分会话，按 user_id + created_at 排序即可拿到对话历史
-- - role 仅支持 user/assistant/system 三种，满足构造 Prompt 所需
-- - status: 1=完成；2=部分（预留）；9=错误（流式异常落库时标记）
-- - 索引 idx_user_created 支撑“按用户取最近N条”的高频查询
CREATE TABLE IF NOT EXISTS ai_chat_message (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id VARCHAR(64) NOT NULL COMMENT '用户ID（当前用0，未来接入真实用户可复用）',
  role ENUM('user','assistant','system') NOT NULL COMMENT '消息角色',
  content LONGTEXT NOT NULL COMMENT '消息内容',
  model VARCHAR(100) NULL COMMENT '生成时使用的模型名',
  tokens INT NULL COMMENT 'Token 粗略估算（可空）',
  metadata JSON NULL COMMENT '扩展元数据(JSON)：如温度、traceId、错误信息等',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1完成，2部分，9错误',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_user_created (user_id, created_at),
  KEY idx_user_role (user_id, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息明细（按用户聚合）';

-- 2) 可选：用户级摘要表（后续做滚动摘要/长上下文优化时启用）
-- 设计说明：
-- - 对用户的历史做凝练，减少 Prompt 拼接长度
-- - 当前不强制使用；如果未来接入RAG或摘要，可直接复用
CREATE TABLE IF NOT EXISTS ai_chat_memory_summary (
  user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
  summary LONGTEXT NOT NULL COMMENT '记忆摘要',
  token_estimate INT NULL COMMENT '摘要的token估算',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户级记忆摘要（可选）';
