-- 双引擎 TTS 配置种子数据
-- 适用场景：已有数据库升级到 GPT-SoVITS / SiliconFlow 双引擎配置版本。
-- 注意：SiliconFlow API Key 不入库，请通过环境变量 SILICONFLOW_API_KEY 配置。

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
  ('tts.speed', '1.0', 'TTS 语速')
ON DUPLICATE KEY UPDATE
  description = VALUES(description);
