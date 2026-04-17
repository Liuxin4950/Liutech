package chat.liuxin.liutech.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import chat.liuxin.liutech.model.dto.TtsConfigDTO;

/**
 * TTS 配置服务（从 system_settings 读写）
 */
@Service
public class TtsConfigService {

    public static final String KEY_ENABLED = "tts.enabled";
    public static final String KEY_BASE_URL = "tts.baseUrl";
    public static final String KEY_VOICE_MODEL = "tts.voiceModel";

    @Autowired
    private SystemSettingService systemSettingService;

    public TtsConfigDTO getConfig() {
        TtsConfigDTO dto = new TtsConfigDTO();
        dto.setEnabled(systemSettingService.getBoolean(KEY_ENABLED, true));
        dto.setBaseUrl(normalizeBaseUrl(systemSettingService.getValue(KEY_BASE_URL)));
        dto.setVoiceModel(normalizeText(systemSettingService.getValue(KEY_VOICE_MODEL)));
        return dto;
    }

    public void updateConfig(TtsConfigDTO config) {
        boolean enabled = config != null && Boolean.TRUE.equals(config.getEnabled());
        String baseUrl = config == null ? null : normalizeBaseUrl(config.getBaseUrl());
        String voiceModel = config == null ? null : normalizeText(config.getVoiceModel());

        systemSettingService.upsert(KEY_ENABLED, Boolean.toString(enabled), "语音推理全局开关：true/false");
        if (baseUrl == null || baseUrl.isBlank()) {
            systemSettingService.upsert(KEY_BASE_URL, "", "语音推理服务基础地址（例如：http://127.0.0.1:8000）");
        } else {
            systemSettingService.upsert(KEY_BASE_URL, baseUrl, "语音推理服务基础地址（例如：http://127.0.0.1:8000）");
        }
        systemSettingService.upsert(KEY_VOICE_MODEL, voiceModel == null ? "" : voiceModel, "语音推理默认语音模型（例如：原神-中文-纳西妲_ZH）");
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private String normalizeText(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        return s.isEmpty() ? null : s;
    }
}
