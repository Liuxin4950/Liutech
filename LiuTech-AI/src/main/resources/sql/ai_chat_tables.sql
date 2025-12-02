-- AI聊天服务数据库表结构与性能优化脚本
-- 作者: 刘鑫
-- 时间: 2025-12-02
-- 说明: 包含表结构定义和性能优化索引

-- 创建数据库
CREATE DATABASE IF NOT EXISTS liutech_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE liutech_ai;

-- 会话表
CREATE TABLE IF NOT EXISTS ai_conversation (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话WWID',
    user_id        VARCHAR(64)     NOT NULL COMMENT '用户ID',
    title          VARCHAR(200)    NULL COMMENT '会话标题（可由首条消息生成）',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),  -- 按用户查询会话
    KEY idx_user_updated (user_id, updated_at DESC)  -- 优化用户会话查询
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话主表';

-- 消息明细表
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id              BIGINT UNSIGNED                    NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id BIGINT UNSIGNED                    NOT NULL COMMENT '会话ID',
    role            ENUM('user','assistant','system')  NOT NULL COMMENT '消息角色',
    content         LONGTEXT                           NULL COMMENT '消息内容（错误时可为空）',
    model           VARCHAR(64)                        NULL COMMENT '生成时使用的模型名',
    tokens          INT UNSIGNED                       NULL COMMENT 'Token总数（输入+输出）',
    status          TINYINT                            NOT NULL DEFAULT 1 COMMENT '状态：1=完成；0=流式中断；2=内容审核拒绝；3=API异常',
    seq_no          INT UNSIGNED                       NOT NULL COMMENT '消息序号（同一会话内顺序）',
    created_at      DATETIME                           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_conversation_seq (conversation_id, seq_no),  -- 按会话+序号高效查询
    KEY idx_conversation_created (conversation_id, created_at),  -- 按会话+时间查询
    KEY idx_created_at (created_at),  -- 按时间排序查询
    KEY idx_status (status),  -- 按状态过滤查询
    KEY idx_conversation_time_seq (conversation_id, created_at DESC, seq_no DESC),  -- 优化消息历史查询
    KEY idx_user_created (conversation_id, created_at DESC),  -- 优化用户消息查询
    KEY idx_status_created (status, created_at DESC),  -- 优化状态过滤查询
    KEY idx_model_created (model, created_at DESC),  -- 优化模型使用统计
    CONSTRAINT fk_conversation 
        FOREIGN KEY (conversation_id) 
        REFERENCES ai_conversation(id) 
        ON DELETE CASCADE  -- 会话删除时自动清理消息
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息明细（按会话组织）';

-- 分析表以更新索引统计信息
ANALYZE TABLE ai_conversation;
ANALYZE TABLE ai_chat_message;

-- 显示表索引信息（用于验证）
SHOW INDEX FROM ai_conversation;
SHOW INDEX FROM ai_chat_message;