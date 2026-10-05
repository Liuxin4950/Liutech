-- 增量迁移：先备份，再在 liutech 主库执行，可安全重放；不自动操作现有环境。
USE liutech;
CREATE TABLE IF NOT EXISTS community_bots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL, avatar_url VARCHAR(1000) DEFAULT NULL,
  personality TEXT NOT NULL, system_prompt TEXT NULL, background TEXT, interests VARCHAR(1000),
  enabled BOOLEAN NOT NULL DEFAULT FALSE, participation INT NOT NULL DEFAULT 50,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at TIMESTAMP(3) NULL,
  CONSTRAINT ck_community_participation CHECK(participation BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 独立检测角色提示词列：旧社区迁移完成后重放也会补上，不依赖 comments.bot_id。
DROP PROCEDURE IF EXISTS migrate_community_bot_prompt;
DELIMITER $$
CREATE PROCEDURE migrate_community_bot_prompt()
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='community_bots' AND column_name='system_prompt') THEN
  ALTER TABLE community_bots ADD COLUMN system_prompt TEXT NULL AFTER personality;
 END IF;
END$$
DELIMITER ;
CALL migrate_community_bot_prompt();
DROP PROCEDURE migrate_community_bot_prompt;
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
INSERT IGNORE INTO community_settings(id) VALUES(1);
CREATE TABLE IF NOT EXISTS community_post_state (
  post_id BIGINT PRIMARY KEY,enabled BOOLEAN NOT NULL DEFAULT TRUE,
  first_public_seen BOOLEAN NOT NULL DEFAULT FALSE,version BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 历史文章都视为已经见过；关闭后重新启用、撤回后重发均不会补评。

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
-- MySQL 8 没有 ADD COLUMN IF NOT EXISTS；以 bot_id 判断整组字段是否已迁移。
DROP PROCEDURE IF EXISTS migrate_community_comments;
DELIMITER $$
CREATE PROCEDURE migrate_community_comments()
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='comments' AND column_name='bot_id') THEN
  INSERT IGNORE INTO community_post_state(post_id,first_public_seen) SELECT id,TRUE FROM posts;
  ALTER TABLE comments MODIFY user_id BIGINT NULL,
  ADD COLUMN bot_id BIGINT NULL,
  ADD COLUMN community_task_id CHAR(36) NULL,
  ADD COLUMN root_event_id CHAR(36) NULL,
  ADD UNIQUE KEY uk_comment_community_task(community_task_id),
  ADD INDEX idx_comments_bot(bot_id),
  ADD CONSTRAINT fk_comment_bot FOREIGN KEY(bot_id) REFERENCES community_bots(id),
  ADD CONSTRAINT ck_comment_author CHECK((user_id IS NULL) <> (bot_id IS NULL));
 END IF;
END$$
DELIMITER ;
CALL migrate_community_comments();
DROP PROCEDURE migrate_community_comments;
-- 发布回执不设评论外键，删除评论也不能重试生成同一个任务。
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
