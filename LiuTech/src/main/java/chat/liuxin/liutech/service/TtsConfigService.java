package chat.liuxin.liutech.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import chat.liuxin.liutech.model.dto.TtsConfigDTO;

/**
 * TTS 配置服务（从 system_settings 读写）
 */
@Service
@RequiredArgsConstructor
 {

    public static final String KEY_ENABLED = "tts.enabled";
    public static final String KEY_BASE_URL = "tts.baseUrl";
    public static final String KEY_VOICE_MODEL = "tts.voiceModel";
    public static final String KEY_PROVIDER = "tts.provider";
    public static final String KEY_SILICON_FLOW_MODEL = "tts.siliconFlowModel";
    public static final String KEY_SILICON_FLOW_VOICE_URI = "tts.siliconFlowVoiceUri";
    public static final String KEY_RESPONSE_FORMAT = "tts.responseFormat";
    public static final String KEY_SAMPLE_RATE = "tts.sampleRate";
    public static final String KEY_SPEED = "tts.speed";

    public static final String PROVIDER_GPT_SOVITS = "GPT_SOVITS";
    public static final String PROVIDER_SILICONFLOW = "SILICONFLOW";

    public static final String DEFAULT_SILICON_FLOW_MODEL = "FunAudioLLM/CosyVoice2-0.5B";
    public static final String DEFAULT_RESPONSE_FORMAT = "mp3";
    public static final int DEFAULT_SAMPLE_RATE = 44100;
    public static final double DEFAULT_SPEED = 1.0;

    private final SystemSettingService systemSettingService;

    public TtsConfigDTO getConfig() {
        TtsConfigDTO dto = new TtsConfigDTO();
        dto.setEnabled(systemSettingService.getBoolean(KEY_ENABLED, true));
        dto.setBaseUrl(normalizeBaseUrl(systemSettingService.getValue(KEY_BASE_URL)));
        dto.setVoiceModel(normalizeText(systemSettingService.getValue(KEY_VOICE_MODEL)));
        dto.setProvider(normalizeProvider(systemSettingService.getValue(KEY_PROVIDER)));
        dto.setSiliconFlowModel(defaultText(systemSettingService.getValue(KEY_SILICON_FLOW_MODEL), DEFAULT_SILICON_FLOW_MODEL));
        dto.setSiliconFlowVoiceUri(normalizeText(systemSettingService.getValue(KEY_SILICON_FLOW_VOICE_URI)));
        dto.setResponseFormat(normalizeResponseFormat(systemSettingService.getValue(KEY_RESPONSE_FORMAT)));
        dto.setSampleRate(parseInteger(systemSettingService.getValue(KEY_SAMPLE_RATE), DEFAULT_SAMPLE_RATE));
        dto.setSpeed(parseDouble(systemSettingService.getValue(KEY_SPEED), DEFAULT_SPEED));
        return dto;
    }

    public void updateConfig(TtsConfigDTO config) {
        boolean enabled = config != null && Boolean.TRUE.equals(config.getEnabled());
        String baseUrl = config == null ? null : normalizeBaseUrl(config.getBaseUrl());
        String voiceModel = config == null ? null : normalizeText(config.getVoiceModel());
        String provider = config == null ? PROVIDER_GPT_SOVITS : normalizeProvider(config.getProvider());
        String siliconFlowModel = config == null ? DEFAULT_SILICON_FLOW_MODEL : defaultText(config.getSiliconFlowModel(), DEFAULT_SILICON_FLOW_MODEL);
        String siliconFlowVoiceUri = config == null ? null : normalizeText(config.getSiliconFlowVoiceUri());
        String responseFormat = config == null ? DEFAULT_RESPONSE_FORMAT : normalizeResponseFormat(config.getResponseFormat());
        Integer sampleRate = config == null ? DEFAULT_SAMPLE_RATE : normalizeSampleRate(config.getSampleRate());
        Double speed = config == null ? DEFAULT_SPEED : normalizeSpeed(config.getSpeed());

        systemSettingService.upsert(KEY_ENABLED, Boolean.toString(enabled), "语音推理全局开关：true/false");
        systemSettingService.upsert(KEY_PROVIDER, provider, "语音推理引擎：GPT_SOVITS/SILICONFLOW");
        if (baseUrl == null || baseUrl.isBlank()) {
            systemSettingService.upsert(KEY_BASE_URL, "", "语音推理服务基础地址（例如：http://127.0.0.1:8000）");
        } else {
            systemSettingService.upsert(KEY_BASE_URL, baseUrl, "语音推理服务基础地址（例如：http://127.0.0.1:8000）");
        }
        systemSettingService.upsert(KEY_VOICE_MODEL, voiceModel == null ? "" : voiceModel, "语音推理默认语音模型（例如：原神-中文-纳西妲_ZH）");
        systemSettingService.upsert(KEY_SILICON_FLOW_MODEL, siliconFlowModel, "SiliconFlow TTS 模型名称");
        systemSettingService.upsert(KEY_SILICON_FLOW_VOICE_URI, siliconFlowVoiceUri == null ? "" : siliconFlowVoiceUri, "SiliconFlow 自定义音色 URI");
        systemSettingService.upsert(KEY_RESPONSE_FORMAT, responseFormat, "TTS 输出音频格式");
        systemSettingService.upsert(KEY_SAMPLE_RATE, Integer.toString(sampleRate), "TTS 输出采样率");
        systemSettingService.upsert(KEY_SPEED, Double.toString(speed), "TTS 语速");
    }

    public void updateSiliconFlowVoiceUri(String uri) {
        String normalized = normalizeText(uri);
        systemSettingService.upsert(KEY_SILICON_FLOW_VOICE_URI, normalized == null ? "" : normalized, "SiliconFlow 自定义音色 URI");
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

    private String defaultText(String raw, String defaultValue) {
        String normalized = normalizeText(raw);
        return normalized == null ? defaultValue : normalized;
    }

    private String normalizeProvider(String raw) {
        String normalized = normalizeText(raw);
        if (normalized == null) return PROVIDER_GPT_SOVITS;
        String upper = normalized.trim().toUpperCase();
        if ("SILICON_FLOW".equals(upper)) return PROVIDER_SILICONFLOW;
        if (PROVIDER_SILICONFLOW.equals(upper)) return PROVIDER_SILICONFLOW;
        return PROVIDER_GPT_SOVITS;
    }

    private String normalizeResponseFormat(String raw) {
        String normalized = normalizeText(raw);
        if (normalized == null) return DEFAULT_RESPONSE_FORMAT;
        String lower = normalized.toLowerCase();
        return switch (lower) {
            case "wav", "opus", "pcm" -> lower;
            default -> DEFAULT_RESPONSE_FORMAT;
        };
    }

    private Integer normalizeSampleRate(Integer value) {
        if (value == null) return DEFAULT_SAMPLE_RATE;
        if (value == 8000 || value == 16000 || value == 24000 || value == 32000 || value == 44100 || value == 48000) {
            return value;
        }
        return DEFAULT_SAMPLE_RATE;
    }

    private Double normalizeSpeed(Double value) {
        if (value == null) return DEFAULT_SPEED;
        return Math.max(0.25, Math.min(4.0, value));
    }

    private Integer parseInteger(String value, int defaultValue) {
        String normalized = normalizeText(value);
        if (normalized == null) return defaultValue;
        try {
            return normalizeSampleRate(Integer.parseInt(normalized));
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    private Double parseDouble(String value, double defaultValue) {
        String normalized = normalizeText(value);
        if (normalized == null) return defaultValue;
        try {
            return normalizeSpeed(Double.parseDouble(normalized));
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
