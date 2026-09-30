-- 冻结的版本0结构测试夹具，基线 commit 4c3578d；无业务数据、无建库语句，不是当前初始化入口。
SET FOREIGN_KEY_CHECKS=0;
CREATE TABLE IF NOT EXISTS ai_tts_config (
  id TINYINT UNSIGNED NOT NULL COMMENT '固定为1的单行配置主键',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '语音功能开关',
  provider VARCHAR(32) NOT NULL DEFAULT 'GPT_SOVITS' COMMENT 'GPT_SOVITS/SILICONFLOW',
  base_url VARCHAR(512) NULL COMMENT 'GPT-SoVITS 服务基础地址',
  voice_model VARCHAR(255) NULL COMMENT 'GPT-SoVITS 默认语音模型',
  siliconflow_model VARCHAR(255) NOT NULL DEFAULT 'FunAudioLLM/CosyVoice2-0.5B' COMMENT 'SiliconFlow TTS 模型',
  siliconflow_voice_uri VARCHAR(512) NULL COMMENT 'SiliconFlow 自定义音色 URI',
  response_format VARCHAR(16) NOT NULL DEFAULT 'mp3' COMMENT 'mp3/wav/opus/pcm',
  sample_rate INT NOT NULL DEFAULT 44100 COMMENT '输出采样率',
  speed DECIMAL(4,2) NOT NULL DEFAULT 1.00 COMMENT '语速，范围0.25-4.00',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  CONSTRAINT chk_ai_tts_config_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI服务TTS配置';

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

CREATE TABLE IF NOT EXISTS ai_model_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  model_name VARCHAR(100) NOT NULL COMMENT '模型名称（如 zai-org/GLM-4.6）',
  display_name VARCHAR(100) NOT NULL COMMENT '显示名称（如 GLM-4.6）',
  provider VARCHAR(50) NOT NULL DEFAULT 'siliconflow' COMMENT '提供商（siliconflow/openai/ollama等）',
  is_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用（0=禁用 1=启用）',
  is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否为默认模型（0=否 1=是，只能有一个默认）',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序顺序（数字越小越靠前）',
  max_tokens INT DEFAULT NULL COMMENT '单次输出上限（token），映射到模型请求的 max_tokens',
  context_window INT DEFAULT NULL COMMENT '模型上下文窗口（输入+输出总 token 上限）；输入预算 = 本值 - max_tokens - 安全余量',
  temperature DECIMAL(3,2) DEFAULT 0.90 COMMENT '默认温度参数',
  description VARCHAR(500) DEFAULT NULL COMMENT '模型描述',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_model_name (model_name) COMMENT '模型名称唯一索引',
  KEY idx_enabled_sort (is_enabled, sort_order) COMMENT '启用状态和排序索引',
  KEY idx_default (is_default) COMMENT '默认模型索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';
SET FOREIGN_KEY_CHECKS=1;
