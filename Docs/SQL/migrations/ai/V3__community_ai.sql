-- 评论区 AI 的独立任务、租约与公共互动记忆。
-- 兼容旧库升级与已部署社区功能的数据库，不复制或重置现有任务和记忆。

CREATE TABLE IF NOT EXISTS ai_community_inbox (
 event_id BIGINT NOT NULL PRIMARY KEY, event_json LONGTEXT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_community_task (
 id VARCHAR(36) NOT NULL PRIMARY KEY, event_id BIGINT NOT NULL, bot_id BIGINT NOT NULL,
 post_id BIGINT NOT NULL, comment_id BIGINT NULL, root_event_id VARCHAR(36) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'READY', attempts INT NOT NULL DEFAULT 0,
 failures INT NOT NULL DEFAULT 0, memory_epoch BIGINT NULL, decision_json LONGTEXT NULL,
 context_version VARCHAR(128) NULL, error VARCHAR(300) NULL, available_at DATETIME(3) NOT NULL,
 lease_until DATETIME(3) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_community_task_event_bot(event_id,bot_id), KEY idx_community_task_ready(status,available_at),
 KEY idx_community_task_bot(bot_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_community_worker (
 id INT NOT NULL PRIMARY KEY, lease_token VARCHAR(36) NULL, lease_until DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_community_run (
 id VARCHAR(36) NOT NULL PRIMARY KEY, task_id VARCHAR(36) NOT NULL,
 bot_id BIGINT NOT NULL, post_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL,
 result_json LONGTEXT NULL, error VARCHAR(300) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_community_run_bot(bot_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_community_role_state (
 bot_id BIGINT NOT NULL PRIMARY KEY, memory_epoch BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_community_memory (
 id VARCHAR(36) NOT NULL PRIMARY KEY, task_id VARCHAR(36) NOT NULL,
 bot_id BIGINT NOT NULL, source_post_id BIGINT NOT NULL, source_comment_id BIGINT NOT NULL,
 summary TEXT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_community_memory_task(task_id), KEY idx_community_memory_bot(bot_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_community_memory_participant (
 memory_id VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL,
 PRIMARY KEY(memory_id,user_id), KEY idx_community_memory_user(user_id),
 CONSTRAINT fk_community_memory_participant FOREIGN KEY(memory_id) REFERENCES ai_community_memory(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_community_user_state (
 user_id BIGINT NOT NULL PRIMARY KEY, purged TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_community_memory_source (
 memory_id VARCHAR(36) NOT NULL, source_comment_id BIGINT NOT NULL,
 PRIMARY KEY(memory_id,source_comment_id),
 CONSTRAINT fk_community_memory_source FOREIGN KEY(memory_id) REFERENCES ai_community_memory(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO ai_community_worker(id, lease_token, lease_until) VALUES(1, NULL, NULL);
