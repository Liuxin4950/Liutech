package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * AI 模型选择与参数策略。
 *
 * 模型选择：出于额度安全考虑，模型完全由服务端决定（数据库配置的默认模型，
 * 未配置时回退 application.yml 默认值），前端不参与模型选择，避免接口被换模型消耗额度。
 *
 * 参数策略：校验 temperature / maxTokens 是否合法，非法则从数据库配置读取或用默认值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelPolicy {

    private final AiModelConfigService aiModelConfigService;
    private final AiChatProperties aiChatProperties;

    /**
     * 解析实际使用的模型名。
     *
     * 模型完全由服务端决定：数据库配置的默认模型，未配置时回退 application.yml 默认值。
     * 忽略请求中可能携带的任何模型参数。
     */
    public String resolveModelName(ChatRequest request) {
        return resolveConfiguredDefaultModel();
    }

    /**
     * 解析实际使用的 temperature / maxTokens。
     *
     * 优先级：请求参数 > 数据库配置 > 默认值。
     * 请求参数越界会被忽略（temperature 需 [0,1]，maxTokens 需 (0, ceiling]）。
     * source 字段标记参数来源（request/database/default），仅用于日志观察。
     */
    public ModelParameters resolveParameters(ChatRequest request, String modelName) {
        Double temperature = request == null ? null : request.getTemperature();
        Integer maxTokens = request == null ? null : request.getMaxTokens();
        String source = "request";

        if (temperature != null && (temperature < 0.0 || temperature > 1.0)) {
            log.warn("前端传递的 temperature 超出范围 [0.0, 1.0]: {}, 将忽略", temperature);
            temperature = null;
        }
        if (maxTokens != null && (maxTokens <= 0 || maxTokens > aiChatProperties.getSecurity().getModelPolicyMaxTokensCeiling())) {
            log.warn("前端传递的 maxTokens 无效或超过上限: {}, 将忽略", maxTokens);
            maxTokens = null;
        }

        if (temperature == null || maxTokens == null) {
            try {
                Optional<ModelConfigDTO> modelConfig = aiModelConfigService.getModelByName(modelName)
                        .filter(config -> Boolean.TRUE.equals(config.getIsEnabled()));
                if (modelConfig.isPresent()) {
                    ModelConfigDTO config = modelConfig.get();
                    if (temperature == null && config.getTemperature() != null) {
                        temperature = config.getTemperature().doubleValue();
                    }
                    if (maxTokens == null && config.getMaxTokens() != null && config.getMaxTokens() > 0) {
                        maxTokens = Math.min(config.getMaxTokens(), aiChatProperties.getSecurity().getModelPolicyMaxTokensCeiling());
                    }
                    source = "database";
                } else {
                    source = "default";
                }
            } catch (Exception e) {
                log.warn("读取模型参数失败，模型: {}, 错误: {}", modelName, e.getMessage());
                source = "default";
            }
        }

        return new ModelParameters(temperature, maxTokens, source);
    }

    /** 从数据库读取当前默认模型；读失败或未配置时回退到 application.yml 的默认值 */
    private String resolveConfiguredDefaultModel() {
        try {
            return aiModelConfigService.getDefaultModel()
                    .filter(config -> Boolean.TRUE.equals(config.getIsEnabled()))
                    .map(ModelConfigDTO::getModelName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .orElse(aiChatProperties.getDefaultModel());
        } catch (Exception e) {
            log.warn("读取默认模型配置失败，使用配置默认模型: {}", e.getMessage());
            return aiChatProperties.getDefaultModel();
        }
    }

    /** 解析后的模型参数三元组：温度、最大 token、参数来源（供日志观察） */
    public record ModelParameters(Double temperature, Integer maxTokens, String source) {
    }
}