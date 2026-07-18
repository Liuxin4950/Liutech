-- ============================================================
-- 文章系列功能迁移脚本（已有库增量升级）
-- 执行前无需备份（测试库），生产环境请先 mysqldump。
-- 库：liutech
-- ============================================================

-- 1. 系列表
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

-- 2. posts 表加系列字段
ALTER TABLE posts ADD COLUMN series_id BIGINT DEFAULT NULL COMMENT '所属系列ID' AFTER author_id;
ALTER TABLE posts ADD COLUMN series_sort INT NOT NULL DEFAULT 0 COMMENT '系列内排序(升序,值越小越靠前)' AFTER series_id;
ALTER TABLE posts ADD INDEX idx_series (series_id, series_sort);
ALTER TABLE posts ADD CONSTRAINT fk_posts_series FOREIGN KEY (series_id) REFERENCES post_series(id) ON DELETE SET NULL;
