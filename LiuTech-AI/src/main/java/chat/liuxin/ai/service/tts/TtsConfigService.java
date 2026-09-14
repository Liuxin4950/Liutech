package chat.liuxin.ai.service.tts;

import chat.liuxin.ai.dto.tts.TtsConfigDTO;
import chat.liuxin.ai.dto.tts.TtsConfigRequest;
import chat.liuxin.ai.entity.AiTtsConfig;
import chat.liuxin.ai.mapper.AiTtsConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


/**
 * TTS 配置服务。配置只存放在 AI 数据库的 ai_tts_config 单行表中。
 */
@Service
@RequiredArgsConstructor
public class TtsConfigService {

    public static final String PROVIDER_GPT_SOVITS = "GPT_SOVITS";
    public static final String PROVIDER_SILICONFLOW = "SILICONFLOW";

    public static final String DEFAULT_SILICON_FLOW_MODEL = "FunAudioLLM/CosyVoice2-0.5B";
    public static final String DEFAULT_RESPONSE_FORMAT = "mp3";
    public static final int DEFAULT_SAMPLE_RATE = 44100;
    public static final double DEFAULT_SPEED = 1.0;

    private static final long CONFIG_ID = 1L;

    private final AiTtsConfigMapper configMapper;

    public TtsConfigDTO getConfig() {
        return toDTO(getOrCreateConfig());
    }

    @Transactional(rollbackFor = Exception.class)
    public TtsConfigDTO updateConfig(TtsConfigRequest config) {
        boolean enabled = config != null && Boolean.TRUE.equals(config.getEnabled());
        String baseUrl = config == null ? null : normalizeBaseUrl(config.getBaseUrl());
        String voiceModel = config == null ? null : normalizeText(config.getVoiceModel());
        String provider = config == null ? PROVIDER_GPT_SOVITS : normalizeProvider(config.getProvider());
        String siliconFlowModel = config == null ? DEFAULT_SILICON_FLOW_MODEL : defaultText(config.getSiliconFlowModel(), DEFAULT_SILICON_FLOW_MODEL);
        String siliconFlowVoiceUri = config == null ? null : normalizeText(config.getSiliconFlowVoiceUri());
        String responseFormat = config == null ? DEFAULT_RESPONSE_FORMAT : normalizeResponseFormat(config.getResponseFormat());
        Integer sampleRate = config == null ? DEFAULT_SAMPLE_RATE : normalizeSampleRate(config.getSampleRate());
        Double speed = config == null ? DEFAULT_SPEED : normalizeSpeed(config.getSpeed());

        AiTtsConfig entity = getOrCreateConfig();
        entity.setEnabled(enabled);
        entity.setProvider(provider);
        entity.setBaseUrl(baseUrl);
        entity.setVoiceModel(voiceModel);
        entity.setSiliconFlowModel(siliconFlowModel);
        entity.setSiliconFlowVoiceUri(siliconFlowVoiceUri);
        entity.setResponseFormat(responseFormat);
        entity.setSampleRate(sampleRate);
        entity.setSpeed(BigDecimal.valueOf(speed));
        configMapper.updateById(entity);
        return toDTO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSiliconFlowVoiceUri(String uri) {
        String normalized = normalizeText(uri);
        AiTtsConfig entity = getOrCreateConfig();
        entity.setSiliconFlowVoiceUri(normalized);
        configMapper.updateById(entity);
    }

    private synchronized AiTtsConfig getOrCreateConfig() {
        AiTtsConfig entity = configMapper.selectById(CONFIG_ID);
        if (entity != null) return entity;

        entity = new AiTtsConfig();
        entity.setId(CONFIG_ID);
        entity.setEnabled(true);
        entity.setProvider(PROVIDER_GPT_SOVITS);
        entity.setSiliconFlowModel(DEFAULT_SILICON_FLOW_MODEL);
        entity.setResponseFormat(DEFAULT_RESPONSE_FORMAT);
        entity.setSampleRate(DEFAULT_SAMPLE_RATE);
        entity.setSpeed(BigDecimal.valueOf(DEFAULT_SPEED));
        configMapper.insert(entity);
        return entity;
    }

    private TtsConfigDTO toDTO(AiTtsConfig entity) {
        TtsConfigDTO dto = new TtsConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setBaseUrl(normalizeBaseUrl(entity.getBaseUrl()));
        dto.setVoiceModel(normalizeText(entity.getVoiceModel()));
        dto.setProvider(normalizeProvider(entity.getProvider()));
        dto.setSiliconFlowModel(defaultText(entity.getSiliconFlowModel(), DEFAULT_SILICON_FLOW_MODEL));
        dto.setSiliconFlowVoiceUri(normalizeText(entity.getSiliconFlowVoiceUri()));
        dto.setResponseFormat(normalizeResponseFormat(entity.getResponseFormat()));
        dto.setSampleRate(normalizeSampleRate(entity.getSampleRate()));
        dto.setSpeed(normalizeSpeed(entity.getSpeed() == null ? null : entity.getSpeed().doubleValue()));
        return dto;
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

}
