-- 评论区 AI 的主库结构。兼容旧库升级、当前完整快照及已部署社区功能的数据库。
-- 仅创建缺失结构与默认暂停配置，不覆盖现有角色、配额、任务或业务数据。

CREATE TABLE IF NOT EXISTS community_bots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL, avatar_url VARCHAR(1000) DEFAULT NULL,
  personality TEXT NOT NULL, background TEXT, interests VARCHAR(1000),
  system_prompt TEXT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE, participation INT NOT NULL DEFAULT 50,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at TIMESTAMP(3) NULL,
  CONSTRAINT ck_community_participation CHECK(participation BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_knowledge (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, bot_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,content MEDIUMTEXT NOT NULL,version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_community_knowledge_bot(bot_id), FOREIGN KEY(bot_id) REFERENCES community_bots(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_settings (
  id BIGINT PRIMARY KEY,enabled BOOLEAN NOT NULL DEFAULT FALSE,
  bot_daily_comment_limit INT NOT NULL DEFAULT 20,site_daily_comment_limit INT NOT NULL DEFAULT 100,
  post_daily_comment_limit INT NOT NULL DEFAULT 20,bot_daily_task_limit INT NOT NULL DEFAULT 40,
  site_daily_task_limit INT NOT NULL DEFAULT 200,min_delay_seconds INT NOT NULL DEFAULT 20,
  max_delay_seconds INT NOT NULL DEFAULT 90,cooldown_seconds INT NOT NULL DEFAULT 30,
  max_chain_comments INT NOT NULL DEFAULT 4,version BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_post_state (
  post_id BIGINT PRIMARY KEY,enabled BOOLEAN NOT NULL DEFAULT TRUE,
  first_public_seen BOOLEAN NOT NULL DEFAULT FALSE,version BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_chains (
  root_event_id CHAR(36) PRIMARY KEY,post_id BIGINT NOT NULL,emitted INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,event_key VARCHAR(120) NOT NULL,event_type VARCHAR(30) NOT NULL,
  bot_id BIGINT NOT NULL,post_id BIGINT NOT NULL,comment_id BIGINT NULL,root_event_id CHAR(36) NOT NULL,
  available_at TIMESTAMP(3) NOT NULL,lease_token CHAR(36) NULL,lease_until TIMESTAMP(3) NULL,
  acknowledged_at TIMESTAMP(3) NULL,created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_community_event_key(event_key),INDEX idx_community_claim(acknowledged_at,available_at,lease_until),
  FOREIGN KEY(bot_id) REFERENCES community_bots(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_publications (
  task_id CHAR(36) PRIMARY KEY,bot_id BIGINT NOT NULL,post_id BIGINT NOT NULL,comment_id BIGINT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  INDEX idx_community_publication_day(created_at),
  INDEX idx_community_publication_bot(bot_id,post_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_attempts (
  task_id CHAR(36) NOT NULL,attempt INT NOT NULL,bot_id BIGINT NOT NULL,post_id BIGINT NOT NULL,
  allowed BOOLEAN NOT NULL,reason VARCHAR(200) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(task_id,attempt),INDEX idx_community_attempt_day(allowed,created_at),
  INDEX idx_community_attempt_bot(bot_id,allowed,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO community_settings
  (id, enabled, bot_daily_comment_limit, site_daily_comment_limit, post_daily_comment_limit,
   bot_daily_task_limit, site_daily_task_limit, min_delay_seconds, max_delay_seconds,
   cooldown_seconds, max_chain_comments, version)
VALUES (1, FALSE, 20, 100, 20, 40, 200, 20, 90, 30, 4, 1);

SET @liutech_community_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='comments' AND column_name='bot_id'),
    'SELECT 1',
    'ALTER TABLE comments ADD COLUMN bot_id BIGINT NULL COMMENT ''机器人作者ID，与user_id恰好一个非空'''
);
PREPARE liutech_community_stmt FROM @liutech_community_sql;
EXECUTE liutech_community_stmt;
DEALLOCATE PREPARE liutech_community_stmt;

SET @liutech_community_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='comments' AND column_name='community_task_id'),
    'SELECT 1',
    'ALTER TABLE comments ADD COLUMN community_task_id CHAR(36) NULL COMMENT ''机器人发布幂等键'''
);
PREPARE liutech_community_stmt FROM @liutech_community_sql;
EXECUTE liutech_community_stmt;
DEALLOCATE PREPARE liutech_community_stmt;

SET @liutech_community_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='comments' AND column_name='root_event_id'),
    'SELECT 1',
    'ALTER TABLE comments ADD COLUMN root_event_id CHAR(36) NULL COMMENT ''共享互聊根链'''
);
PREPARE liutech_community_stmt FROM @liutech_community_sql;
EXECUTE liutech_community_stmt;
DEALLOCATE PREPARE liutech_community_stmt;

SET @liutech_community_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='comments' AND column_name='user_id' AND is_nullable='YES'),
    'SELECT 1',
    'ALTER TABLE comments MODIFY COLUMN user_id BIGINT NULL COMMENT ''真人评论者ID，机器人评论为空'''
);
PREPARE liutech_community_stmt FROM @liutech_community_sql;
EXECUTE liutech_community_stmt;
DEALLOCATE PREPARE liutech_community_stmt;

SET @liutech_community_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='comments' AND index_name='uk_comment_community_task'),
    'SELECT 1',
    'ALTER TABLE comments ADD UNIQUE INDEX uk_comment_community_task (community_task_id)'
);
PREPARE liutech_community_stmt FROM @liutech_community_sql;
EXECUTE liutech_community_stmt;
DEALLOCATE PREPARE liutech_community_stmt;

SET @liutech_community_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='comments' AND index_name='idx_comments_bot'),
    'SELECT 1',
    'ALTER TABLE comments ADD INDEX idx_comments_bot (bot_id)'
);
PREPARE liutech_community_stmt FROM @liutech_community_sql;
EXECUTE liutech_community_stmt;
DEALLOCATE PREPARE liutech_community_stmt;

SET @liutech_community_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='comments' AND constraint_name='fk_comment_bot'),
    'SELECT 1',
    'ALTER TABLE comments ADD CONSTRAINT fk_comment_bot FOREIGN KEY (bot_id) REFERENCES community_bots(id)'
);
PREPARE liutech_community_stmt FROM @liutech_community_sql;
EXECUTE liutech_community_stmt;
DEALLOCATE PREPARE liutech_community_stmt;

SET @liutech_community_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='comments' AND constraint_name='ck_comment_author'),
    'SELECT 1',
    'ALTER TABLE comments ADD CONSTRAINT ck_comment_author CHECK ((user_id IS NULL) <> (bot_id IS NULL))'
);
PREPARE liutech_community_stmt FROM @liutech_community_sql;
EXECUTE liutech_community_stmt;
DEALLOCATE PREPARE liutech_community_stmt;

SET @liutech_community_sql = NULL;
