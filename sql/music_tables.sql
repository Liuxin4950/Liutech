-- 音乐播放功能表结构
-- 作者: 刘鑫
-- 时间: 2025-01-01

USE liutech;

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
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐表';
