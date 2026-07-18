-- ============================================================================
-- LiuTech 全栈初始化脚本 (唯一权威版本)
-- 
-- 本文件是项目唯一的数据库初始化脚本，同时初始化主后端库 liutech 和 AI 服务库 liutech_ai。
-- Docker 部署时通过 docker-entrypoint-initdb.d 自动执行（仅首次初始化）。
--
-- 幂等性保证：
--   - 所有 CREATE TABLE 使用 IF NOT EXISTS，重复执行不会报错。
--   - 所有 INSERT 使用 IGNORE 或 ON DUPLICATE KEY UPDATE，避免主键冲突。
--   - 所有 ALTER TABLE 包含条件判断，跳过已存在的列/索引。
--
-- 历史迁移合并（2026-05-08）：
--   - sql/ai_chat_tables.sql         → 合并到本文件
--   - sql/ai_agent_migration_*.sql   → 合并到本文件
--   - sql/tts_dual_provider_*.sql    → 合并到本文件
--
-- 已有环境使用说明：
--   如果数据库已通过旧脚本初始化过，直接执行本文件不会破坏现有数据
--   （CREATE IF NOT EXISTS 和 INSERT IGNORE 保证安全）。
--   只需确保缺失的表/数据被补齐即可。
-- ============================================================================
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
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '用户角色(user/admin)',
  status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '用户状态(0禁用,1正常)',
  last_login_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近登录时间',
  nickname VARCHAR(100) DEFAULT NULL COMMENT '用户昵称',
  bio TEXT DEFAULT NULL COMMENT '个人简介',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_role (role),
  INDEX idx_points (points) COMMENT '积分索引，用于排行榜',
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 初始化 admin 用户（如不存在则插入，已存在则确保角色为管理员）
INSERT INTO users (username, email, password_hash, role, status, points, nickname)
VALUES ('admin', 'admin@liuxin.chat', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin', 1, 0, '管理员')
ON DUPLICATE KEY UPDATE role = 'admin';

CREATE TABLE IF NOT EXISTS categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
  name VARCHAR(150) NOT NULL UNIQUE COMMENT '分类名',
  description VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类表';

CREATE TABLE IF NOT EXISTS tags (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '标签ID',
  name VARCHAR(100) NOT NULL UNIQUE COMMENT '标签名',
  description VARCHAR(255) DEFAULT NULL COMMENT '标签描述',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

CREATE TABLE IF NOT EXISTS post_series (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '系列ID',
  name VARCHAR(150) NOT NULL UNIQUE COMMENT '系列名',
  description VARCHAR(500) DEFAULT NULL COMMENT '系列描述',
  cover_image VARCHAR(512) DEFAULT NULL COMMENT '系列封面图URL',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章系列表';

CREATE TABLE IF NOT EXISTS posts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文章ID',
  title VARCHAR(255) NOT NULL COMMENT '文章标题',
  content LONGTEXT NOT NULL COMMENT '文章内容（Markdown）',
  summary VARCHAR(500) DEFAULT NULL COMMENT '摘要',
  cover_image VARCHAR(512) DEFAULT NULL COMMENT '封面图片URL',
  thumbnail VARCHAR(512) DEFAULT NULL COMMENT '缩略图URL',
  category_id BIGINT NOT NULL COMMENT '分类ID',
  author_id BIGINT NOT NULL COMMENT '作者ID',
  series_id BIGINT DEFAULT NULL COMMENT '所属系列ID',
  series_sort INT NOT NULL DEFAULT 0 COMMENT '系列内排序(升序,值越小越靠前)',
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
  INDEX idx_category_status (category_id, status),
  INDEX idx_author_status (author_id, status),
  INDEX idx_series (series_id, series_sort),
  INDEX idx_deleted_at (deleted_at),
  INDEX idx_status_deleted_created (status, deleted_at, created_at),
  FOREIGN KEY (category_id) REFERENCES categories(id),
  FOREIGN KEY (author_id) REFERENCES users(id),
  FOREIGN KEY (series_id) REFERENCES post_series(id) ON DELETE SET NULL
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
  INDEX idx_deleted_at (deleted_at),
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
  INDEX idx_parent_id (parent_id),
  INDEX idx_deleted_at (deleted_at),
  FOREIGN KEY (post_id) REFERENCES posts(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (parent_id) REFERENCES comments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

CREATE TABLE IF NOT EXISTS resources (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '资源ID',
  name VARCHAR(255) NOT NULL COMMENT '资源名称',
  description VARCHAR(1000) DEFAULT NULL COMMENT '资源描述',
  file_url VARCHAR(512) DEFAULT NULL COMMENT '文件存储路径或URL（链接类型可为空）',
  external_link VARCHAR(2048) DEFAULT NULL COMMENT '外部链接（网盘、其他网站等）',
  resource_type ENUM('file', 'link', 'both') DEFAULT 'file' COMMENT '资源类型：file=上传文件，link=外部链接，both=两者都有',
  purchased_note TEXT DEFAULT NULL COMMENT '购买后显示的说明（提取码、使用说明等）',
  uploader_id BIGINT NOT NULL COMMENT '上传用户ID',
  download_type TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '下载类型（0免费，1积分）',
  points_needed DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '下载所需积分',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_uploader_id (uploader_id),
  INDEX idx_resource_type (resource_type),
  INDEX idx_deleted_at (deleted_at),
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
  UNIQUE KEY uk_user_resource (user_id, resource_id) COMMENT '用户资源唯一索引，防止重复购买',
  INDEX idx_user_id (user_id),
  INDEX idx_resource_id (resource_id),
  INDEX idx_deleted_at (deleted_at),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (resource_id) REFERENCES resources(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下载记录表';

-- 积分流水表（记录所有积分变动）
CREATE TABLE IF NOT EXISTS points_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流水ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  transaction_type VARCHAR(20) NOT NULL COMMENT '交易类型(checkin签到/consumption消费/refund退款/admin_adjust管理员调整)',
  amount DECIMAL(12,2) NOT NULL COMMENT '变动金额（正数为增加，负数为减少）',
  balance_after DECIMAL(12,2) NOT NULL COMMENT '变动后余额',
  source_type VARCHAR(50) DEFAULT NULL COMMENT '来源类型(resource_download/admin_manual/system_reward等)',
  source_id BIGINT DEFAULT NULL COMMENT '来源ID（资源ID等）',
  description VARCHAR(500) DEFAULT NULL COMMENT '描述',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_user_id (user_id),
  INDEX idx_transaction_type (transaction_type),
  INDEX idx_created_at (created_at),
  INDEX idx_source (source_type, source_id),
  INDEX idx_user_created (user_id, created_at),
  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

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
  INDEX idx_end_time (end_time),
  INDEX idx_deleted_at (deleted_at)
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
  INDEX idx_deleted_at (deleted_at),
  FOREIGN KEY (post_id) REFERENCES posts(id),
  FOREIGN KEY (resource_id) REFERENCES resources(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章附件表（草稿态与文章态通用）';

-- 音乐表
CREATE TABLE IF NOT EXISTS music (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '音乐ID',
    title           VARCHAR(200)    NOT NULL COMMENT '歌曲名',
    artist          VARCHAR(100)    NULL COMMENT '艺术家',
    cover_url       VARCHAR(500)    NULL COMMENT '封面图URL',
    full_audio_url  VARCHAR(500)    NOT NULL COMMENT '完整音频URL',
    vocal_url       VARCHAR(500)    NOT NULL COMMENT '人声音频URL(模型对口型)',
    duration        INT             NULL COMMENT '时长(秒)',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序权重',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1=启用, 0=禁用',
    deleted_at      DATETIME        NULL COMMENT '删除时间(软删除)',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_order (sort_order),
    KEY idx_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐表';

-- 管理端操作日志表
CREATE TABLE IF NOT EXISTS system_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
  operator VARCHAR(100) NOT NULL COMMENT '操作人用户名',
  operator_id BIGINT DEFAULT NULL COMMENT '操作人ID',
  action VARCHAR(50) NOT NULL COMMENT '操作类型(登录/创建/更新/删除/恢复/发布/下线等)',
  target_type VARCHAR(50) DEFAULT NULL COMMENT '目标类型(post/user/category/tag/announcement等)',
  target_id BIGINT DEFAULT NULL COMMENT '目标ID',
  target_name VARCHAR(255) DEFAULT NULL COMMENT '目标名称',
  description TEXT DEFAULT NULL COMMENT '操作描述',
  ip VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
  user_agent VARCHAR(500) DEFAULT NULL COMMENT '浏览器User-Agent',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0失败/1成功)',
  error_message TEXT DEFAULT NULL COMMENT '错误信息',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_operator (operator),
  INDEX idx_operator_id (operator_id),
  INDEX idx_action (action),
  INDEX idx_target_type (target_type),
  INDEX idx_target_id (target_id),
  INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端操作日志表';

-- 系统设置表（用于存储可动态配置的开关/地址等）
CREATE TABLE IF NOT EXISTS system_settings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设置ID',
  setting_key VARCHAR(128) NOT NULL COMMENT '配置键（全局唯一）',
  setting_value TEXT NULL COMMENT '配置值（字符串存储，必要时可存JSON）',
  description VARCHAR(255) NULL COMMENT '配置说明',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_setting_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置表';

INSERT INTO system_settings (setting_key, setting_value, description)
VALUES
  ('tts.enabled', 'true', '语音推理全局开关：true/false'),
  ('tts.provider', 'GPT_SOVITS', '语音推理引擎：GPT_SOVITS/SILICONFLOW'),
  ('tts.baseUrl', '', 'GPT-SoVITS 语音推理服务基础地址（例如：http://127.0.0.1:8000）'),
  ('tts.voiceModel', '', 'GPT-SoVITS 默认语音模型（例如：原神-中文-纳西妲_ZH）'),
  ('tts.siliconFlowModel', 'FunAudioLLM/CosyVoice2-0.5B', 'SiliconFlow TTS 模型名称'),
  ('tts.siliconFlowVoiceUri', '', 'SiliconFlow 自定义音色 URI'),
  ('tts.responseFormat', 'mp3', 'TTS 输出音频格式'),
  ('tts.sampleRate', '44100', 'TTS 输出采样率'),
  ('tts.speed', '1.0', 'TTS 语速'),
  ('site.name', 'LiuTech', '站点名称'),
  ('site.description', '', '站点描述（SEO description）'),
  ('site.keywords', '', 'SEO 关键词（逗号分隔）'),
  ('site.logo_url', '', '站点 Logo URL'),
  ('site.favicon_url', '', 'Favicon URL'),
  ('site.footer_text', '', '页脚文本'),
  ('site.icp_number', '', 'ICP 备案号'),
  ('site.analytics_code', '', '统计代码（如 Google Analytics）'),
  ('comment.need_review', 'true', '评论是否需要审核（true/false）'),
  ('upload.max_size_mb', '100', '上传文件最大大小（MB）'),
  ('author.name', '小鑫同学', '作者昵称（首页侧边栏展示）'),
  ('author.title', '欢迎访问', '作者头衔/职位'),
  ('author.avatar', '/洛天依.png', '作者头像 URL'),
  ('author.bio', '专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。', '作者个人简介')
ON DUPLICATE KEY UPDATE
  description = VALUES(description);

-- 轮播图表
CREATE TABLE IF NOT EXISTS carousels (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '轮播图ID',
  title VARCHAR(255) NOT NULL COMMENT '标题',
  image_url VARCHAR(512) NOT NULL COMMENT '图片URL',
  link_url VARCHAR(512) DEFAULT NULL COMMENT '跳转链接',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序（越大越靠前）',
  status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态(0禁用,1启用)',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_sort_order (sort_order),
  INDEX idx_status (status),
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轮播图表';

-- 图片表（用于图片上传去重）
CREATE TABLE IF NOT EXISTS images (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '图片ID',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_url VARCHAR(512) NOT NULL COMMENT '图片访问URL',
  file_path VARCHAR(512) NOT NULL COMMENT '文件存储相对路径',
  file_hash VARCHAR(64) NOT NULL COMMENT '文件哈希值（SHA-256，用于去重）',
  file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
  mime_type VARCHAR(100) DEFAULT NULL COMMENT 'MIME类型',
  extension VARCHAR(20) NOT NULL COMMENT '文件扩展名',
  width INT DEFAULT NULL COMMENT '图片宽度（像素）',
  height INT DEFAULT NULL COMMENT '图片高度（像素）',
  uploader_id BIGINT NOT NULL COMMENT '上传用户ID',
  usage_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '引用次数（被多少篇文章使用）',
  status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态（0禁用，1正常）',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_file_hash (file_hash) COMMENT '哈希索引，用于去重查询',
  INDEX idx_uploader_id (uploader_id) COMMENT '上传者索引',
  INDEX idx_status (status) COMMENT '状态索引',
  INDEX idx_deleted_at (deleted_at) COMMENT '软删除索引',
  FOREIGN KEY (uploader_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片表（支持去重）';

CREATE TABLE IF NOT EXISTS messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '留言ID',
  nickname VARCHAR(100) NOT NULL COMMENT '留言者昵称',
  email VARCHAR(320) NOT NULL COMMENT '留言者邮箱',
  content TEXT NOT NULL COMMENT '留言内容',
  status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态(0待审核,1已审核,2已拒绝)',
  reply TEXT DEFAULT NULL COMMENT '管理员回复',
  replied_at TIMESTAMP NULL DEFAULT NULL COMMENT '回复时间',
  replied_by BIGINT DEFAULT NULL COMMENT '回复管理员ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '留言时间',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
  deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '软删除时间',
  INDEX idx_status (status),
  INDEX idx_created_at (created_at),
  INDEX idx_email (email),
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客留言表';

-- 验证码表（用于忘记密码、邮箱验证码登录等场景，属于主后端库 liutech）
CREATE TABLE IF NOT EXISTS verification_codes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  email VARCHAR(320) NOT NULL COMMENT '邮箱地址',
  code VARCHAR(10) NOT NULL COMMENT '验证码',
  type VARCHAR(30) NOT NULL COMMENT '类型：REGISTER/FORGOT_PASSWORD/EMAIL_LOGIN',
  used TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用(0未使用,1已使用)',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '错误尝试次数',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
  PRIMARY KEY (id),
  KEY idx_email_type_used_exp (email, type, used, expires_at),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码表';

-- 重新开启外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================
-- AI 服务数据库表结构（liutech_ai 库）
-- =========================================================
CREATE DATABASE IF NOT EXISTS liutech_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE liutech_ai;

SET FOREIGN_KEY_CHECKS = 0;

-- AI 会话表
CREATE TABLE IF NOT EXISTS ai_conversation
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID，主键',
    user_id         VARCHAR(64)     NOT NULL COMMENT '用户ID',
    title           VARCHAR(200)    NULL COMMENT '会话标题（可重命名）',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '会话状态：0=正常, 9=已归档',
    message_count   INT             NOT NULL DEFAULT 0 COMMENT '会话中的消息总数',
    last_message_at DATETIME        NULL COMMENT '最后消息时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_last (user_id, last_message_at) COMMENT '按用户和最后消息时间查询',
    KEY idx_user_status (user_id, status) COMMENT '按用户和状态查询'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI会话表';

-- AI 聊天消息表
CREATE TABLE IF NOT EXISTS ai_chat_message
(
    id              BIGINT UNSIGNED                    NOT NULL AUTO_INCREMENT COMMENT '消息ID，主键',
    conversation_id BIGINT UNSIGNED                    NOT NULL COMMENT '会话ID（外键关联 ai_conversation.id）',
    user_id         VARCHAR(64)                        NOT NULL COMMENT '用户ID',
    role            ENUM ('user','assistant','system') NOT NULL COMMENT '消息角色：user=用户, assistant=AI, system=系统',
    content         LONGTEXT                           NULL COMMENT '消息内容',
    seq_no          INT                                NOT NULL DEFAULT 0 COMMENT '消息序号（会话内排序）',
    model           VARCHAR(100)                       NULL COMMENT '使用的AI模型名',
    tokens          INT                                NULL COMMENT 'Token使用量估算',
    metadata        JSON                               NULL COMMENT '扩展元数据（JSON格式）',
    status          TINYINT                            NOT NULL DEFAULT 1 COMMENT '消息状态：0=流式中断, 1=完成, 2=内容审核拒绝, 3=API异常',
    created_at      DATETIME                           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME                           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at) COMMENT '按用户和时间查询历史',
    KEY idx_user_role (user_id, role) COMMENT '按用户和角色查询',
    KEY idx_conv_created (conversation_id, created_at) COMMENT '按会话查询消息',
    KEY idx_conv_seq (conversation_id, seq_no) COMMENT '按会话和序号查询',
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI聊天消息表';

-- 用户记忆摘要表（预留表，暂未使用 — 项目中无任何代码引用此表）

-- AI 模型配置表
CREATE TABLE IF NOT EXISTS ai_model_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  model_name VARCHAR(100) NOT NULL COMMENT '模型名称（如 zai-org/GLM-4.6）',
  display_name VARCHAR(100) NOT NULL COMMENT '显示名称（如 GLM-4.6）',
  provider VARCHAR(50) NOT NULL DEFAULT 'siliconflow' COMMENT '提供商（siliconflow/openai/ollama等）',
  is_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用（0=禁用 1=启用）',
  is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否为默认模型（0=否 1=是，只能有一个默认）',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序顺序（数字越小越靠前）',
  max_tokens INT DEFAULT NULL COMMENT '最大token数限制',
  temperature DECIMAL(3,2) DEFAULT 0.90 COMMENT '默认温度参数',
  description VARCHAR(500) DEFAULT NULL COMMENT '模型描述',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_model_name (model_name) COMMENT '模型名称唯一索引',
  KEY idx_enabled_sort (is_enabled, sort_order) COMMENT '启用状态和排序索引',
  KEY idx_default (is_default) COMMENT '默认模型索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';

INSERT INTO ai_model_config
  (model_name, display_name, provider, is_enabled, is_default, sort_order, max_tokens, temperature, description)
VALUES
  ('zai-org/GLM-4.6', 'GLM-4.6', 'siliconflow', 1, 1, 1, 8192, 0.90, '智谱AI GLM-4.6模型，通用对话能力强（默认）'),
  ('Qwen/Qwen2.5-7B-Instruct', 'Qwen2.5-7B', 'siliconflow', 1, 0, 2, 8192, 0.90, '阿里通义千问2.5-7B指令模型'),
  ('deepseek-ai/DeepSeek-V2.5', 'DeepSeek-V2.5', 'siliconflow', 0, 0, 3, 4096, 0.70, '深度求索V2.5模型，推理能力强')
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  provider = VALUES(provider),
  is_enabled = VALUES(is_enabled),
  is_default = VALUES(is_default),
  sort_order = VALUES(sort_order),
  max_tokens = VALUES(max_tokens),
  temperature = VALUES(temperature),
  description = VALUES(description);
