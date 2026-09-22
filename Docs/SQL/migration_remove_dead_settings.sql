-- ============================================================
-- 清理 system_settings 中的死配置（已有库增量升级）
--
-- 背景：
--   1) tts.*（9 行）原由主后端旧 TtsConfigService 消费；2026-09-14 TTS 解耦到
--      AI 服务（dfbdb90）后消费方已删除，配置唯一事实源改为 liutech_ai.ai_tts_config。
--      解耦时按回滚方案保留 7 天，回滚窗口已过，现清理。
--   2) site.* / comment.need_review / upload.max_size_mb 从未有真实消费方：
--      站点资料走 Web/src/config/site.ts 与 VITE_* 环境变量；评论无审核逻辑；
--      上传上限由 Spring multipart 与 file.upload.* 配置控制。
--
-- 保留：author.*、about.*（关于页管理，AboutPageService/UserProfileService 真实消费）。
--
-- 执行前生产环境请先 mysqldump 备份 liutech 库。
-- 库：liutech
-- 幂等：可重复执行。
-- ============================================================

DELETE FROM system_settings
WHERE setting_key IN (
  -- 旧 TTS 配置（已迁至 liutech_ai.ai_tts_config）
  'tts.enabled',
  'tts.provider',
  'tts.baseUrl',
  'tts.voiceModel',
  'tts.siliconFlowModel',
  'tts.siliconFlowVoiceUri',
  'tts.responseFormat',
  'tts.sampleRate',
  'tts.speed',
  -- 从未被消费的站点/评论/上传占位配置
  'site.name',
  'site.description',
  'site.keywords',
  'site.logo_url',
  'site.favicon_url',
  'site.footer_text',
  'site.icp_number',
  'site.analytics_code',
  'comment.need_review',
  'upload.max_size_mb'
);

-- 预期影响：旧库最多删除 19 行（9 行 tts.* + 8 行 site.* + comment.need_review + upload.max_size_mb）；
-- author.* 与 about.content 不受影响。
