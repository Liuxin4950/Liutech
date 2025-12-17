-- 关闭外键检查，避免顺序限制导致错误
SET FOREIGN_KEY_CHECKS = 0;
-- 创建数据库
CREATE DATABASE IF NOT EXISTS liutech DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 切换到liutech数据库
USE liutech;


CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
  username VARCHAR(150) NOT NULL UNIQUE COMMENT '用户名',
  email VARCHAR(320) NOT NULL UNIQUE COMMENT '邮箱',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  avatar_url VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
  points DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '用户积分',
  status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '用户状态(0禁用,1正常)',
  last_login_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近登录时间',
  nickname VARCHAR(100) DEFAULT NULL COMMENT '用户昵称',
  bio TEXT DEFAULT NULL COMMENT '个人简介',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_username (username),
  INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
  name VARCHAR(150) NOT NULL UNIQUE COMMENT '分类名',
  description VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类表';

CREATE TABLE IF NOT EXISTS tags (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '标签ID',
  name VARCHAR(100) NOT NULL UNIQUE COMMENT '标签名',
  description VARCHAR(255) DEFAULT NULL COMMENT '标签描述',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

CREATE TABLE IF NOT EXISTS posts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文章ID',
  title VARCHAR(255) NOT NULL COMMENT '文章标题',
  content LONGTEXT NOT NULL COMMENT '文章内容（Markdown）',
  summary VARCHAR(500) DEFAULT NULL COMMENT '摘要',
  cover_image VARCHAR(512) DEFAULT NULL COMMENT '封面图片URL',
  thumbnail VARCHAR(512) DEFAULT NULL COMMENT '缩略图URL',
  category_id BIGINT NOT NULL COMMENT '分类ID',
  author_id BIGINT NOT NULL COMMENT '作者ID',
  status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '文章状态(draft草稿,published已发布,archived已归档)',
  view_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览次数',
  like_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
  favorite_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏数',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_category_id (category_id),
  INDEX idx_author_id (author_id),
  INDEX idx_status (status),
  FOREIGN KEY (category_id) REFERENCES categories(id),
  FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

CREATE TABLE IF NOT EXISTS post_tags (
  post_id BIGINT NOT NULL COMMENT '文章ID',
  tag_id BIGINT NOT NULL COMMENT '标签ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (post_id, tag_id),
  FOREIGN KEY (post_id) REFERENCES posts(id),
  FOREIGN KEY (tag_id) REFERENCES tags(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章-标签关联表';

-- 点赞表
CREATE TABLE IF NOT EXISTS post_likes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '点赞ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  post_id BIGINT NOT NULL COMMENT '文章ID',
  is_like TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否点赞(0取消点赞,1点赞)',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '删除时间（软删除）',
  UNIQUE KEY uk_user_post (user_id, post_id) COMMENT '用户文章唯一索引',
  INDEX idx_user_id (user_id),
  INDEX idx_post_id (post_id),
  INDEX idx_deleted_at (deleted_at),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (post_id) REFERENCES posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章点赞表';

-- 收藏表
CREATE TABLE IF NOT EXISTS post_favorites (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '收藏ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  post_id BIGINT NOT NULL COMMENT '文章ID',
  is_favorite TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否收藏(0取消收藏,1收藏)',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '删除时间（软删除）',
  UNIQUE KEY uk_user_post (user_id, post_id) COMMENT '用户文章唯一索引',
  INDEX idx_user_id (user_id),
  INDEX idx_post_id (post_id),
  INDEX idx_deleted_at (deleted_at),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (post_id) REFERENCES posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章收藏表';

CREATE TABLE IF NOT EXISTS comments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评论ID',
  post_id BIGINT NOT NULL COMMENT '文章ID',
  user_id BIGINT NOT NULL COMMENT '评论者用户ID',
  content TEXT NOT NULL COMMENT '评论内容',
  parent_id BIGINT DEFAULT NULL COMMENT '父评论ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_post_id (post_id),
  INDEX idx_user_id (user_id),
  FOREIGN KEY (post_id) REFERENCES posts(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (parent_id) REFERENCES comments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

CREATE TABLE IF NOT EXISTS resources (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '资源ID',
  name VARCHAR(255) NOT NULL COMMENT '资源名称',
  description VARCHAR(1000) DEFAULT NULL COMMENT '资源描述',
  file_url VARCHAR(512) NOT NULL COMMENT '文件存储路径或URL',
  uploader_id BIGINT NOT NULL COMMENT '上传用户ID',
  download_type TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '下载类型（0免费，1积分）',
  points_needed DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '下载所需积分',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_uploader_id (uploader_id),
  FOREIGN KEY (uploader_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源表';

CREATE TABLE IF NOT EXISTS download_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
  user_id BIGINT NOT NULL COMMENT '下载用户ID',
  resource_id BIGINT NOT NULL COMMENT '资源ID',
  points_used DECIMAL(12,2) DEFAULT 0 COMMENT '使用积分',
  downloaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下载时间',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_user_id (user_id),
  INDEX idx_resource_id (resource_id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (resource_id) REFERENCES resources(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下载记录表';

CREATE TABLE IF NOT EXISTS announcements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公告ID',
  title VARCHAR(255) NOT NULL COMMENT '公告标题',
  content TEXT NOT NULL COMMENT '公告内容',
  type TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '公告类型(1系统,2活动,3维护,4其他)',
  priority TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '优先级(1低,2中,3高,4紧急)',
  status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态(0草稿,1发布,2下线)',
  start_time TIMESTAMP NULL DEFAULT NULL COMMENT '开始显示时间',
  end_time TIMESTAMP NULL DEFAULT NULL COMMENT '结束显示时间',
  is_top TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否置顶(0否,1是)',
  view_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '查看次数',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_status (status),
  INDEX idx_type (type),
  INDEX idx_priority (priority),
  INDEX idx_is_top (is_top),
  INDEX idx_start_time (start_time),
  INDEX idx_end_time (end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

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

-- 新增：文章附件表（草稿与正式文章通用关联）
CREATE TABLE IF NOT EXISTS post_attachments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '附件关联ID',
  draft_key VARCHAR(64) DEFAULT NULL COMMENT '草稿关联键（未创建文章前使用）',
  post_id BIGINT DEFAULT NULL COMMENT '文章ID（创建文章后绑定）',
  resource_id BIGINT NOT NULL COMMENT '资源ID（resources表主键）',
  type VARCHAR(50) NOT NULL DEFAULT 'resource' COMMENT '附件类型（image, document, resource等）',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_draft_key (draft_key),
  INDEX idx_post_id (post_id),
  INDEX idx_resource_id (resource_id),
  FOREIGN KEY (post_id) REFERENCES posts(id),
  FOREIGN KEY (resource_id) REFERENCES resources(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章附件表（草稿态与文章态通用）';


-- 1) 必选：消息明细表（按用户时间线组织）
-- 设计说明：
-- - 不区分会话，按 user_id + created_at 排序即可拿到对话历史
-- - role 仅支持 user/assistant/system 三种，满足构造 Prompt 所需
-- - status: 1=完成；2=部分（预留）；9=错误（流式异常落库时标记）
-- - 索引 idx_user_created 支撑“按用户取最近N条”的高频查询
CREATE TABLE IF NOT EXISTS ai_chat_message
(
    id              BIGINT UNSIGNED                    NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id BIGINT UNSIGNED                    NOT NULL COMMENT '会话ID',
    user_id         VARCHAR(64)                        NOT NULL COMMENT '用户ID（当前用0，未来接入真实用户可复用）',
    role            ENUM ('user','assistant','system') NOT NULL COMMENT '消息角色',
    content         LONGTEXT                           NULL COMMENT '消息内容（错误时可为空）',
    model           VARCHAR(100)                       NULL COMMENT '生成时使用的模型名',
    tokens          INT                                NULL COMMENT 'Token 粗略估算（可空）',
    metadata        JSON                               NULL COMMENT '扩展元数据(JSON)：如温度、traceId、错误信息等',
    status          TINYINT                            NOT NULL DEFAULT 1 COMMENT '状态：1完成，2部分，9错误',
    created_at      DATETIME                           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME                           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at),
    KEY idx_user_role (user_id, role),
    KEY idx_conv_created (conversation_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI聊天消息明细（按用户聚合）';

-- 2) 可选：用户级摘要表（后续做滚动摘要/长上下文优化时启用）
-- 设计说明：
-- - 对用户的历史做凝练，减少 Prompt 拼接长度
-- - 当前不强制使用；如果未来接入RAG或摘要，可直接复用
CREATE TABLE IF NOT EXISTS ai_chat_memory_summary
(
    user_id        VARCHAR(64) NOT NULL COMMENT '用户ID',
    summary        LONGTEXT    NOT NULL COMMENT '记忆摘要',
    token_estimate INT         NULL COMMENT '摘要的token估算',
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户级记忆摘要（可选）';
CREATE TABLE IF NOT EXISTS ai_conversation
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(64)     NOT NULL,
    type            VARCHAR(32)     NOT NULL,
    title           VARCHAR(200)    NULL,
    status          TINYINT         NOT NULL DEFAULT 0,
    message_count   INT             NOT NULL DEFAULT 0,
    last_message_at DATETIME        NULL,
    metadata        JSON            NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_type (user_id, type),
    KEY idx_user_last (user_id, last_message_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI会话';



-- 重新开启外键检查
SET FOREIGN_KEY_CHECKS = 1;
