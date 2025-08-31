-- 为post_likes表添加deleted_at字段
ALTER TABLE post_likes ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '删除时间（软删除）';
ALTER TABLE post_likes ADD INDEX idx_deleted_at (deleted_at);

-- 为post_favorites表添加deleted_at字段
ALTER TABLE post_favorites ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL COMMENT '删除时间（软删除）';
ALTER TABLE post_favorites ADD INDEX idx_deleted_at (deleted_at);