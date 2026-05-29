package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelPolicy {

    private final AiModelConfigService aiModelConfigService;

    @Value("${spring.ai.openai.chat.options.model:zai-org/GLM-4.6}")
    private String configuredDefaultModel;

    @Value("${spring.ai.security.model-policy.strict-whitelist:true}")
    private boolean strictWhitelist;

    @Value("${spring.ai.security.model-policy.max-tokens-ceiling:4096}")
    private int maxTokensCeiling;

    public String resolveModelName(ChatRequest request) {
        return resolveModelName(request == null ? null : request.getModel());
    }

    public String resolveModelName(String requestedModel) {
        String requested = trimToNull(requestedModel);
        String fallback = resolveConfiguredDefaultModel();
        List<ModelConfigDTO> enabledModels = safeEnabledModels();

        if (requested == null) {
            return fallback;
        }

        Optional<ModelConfigDTO> requestedConfig = aiModelConfigService.getModelByName(requested)
                .filter(config -> Boolean.TRUE.equals(config.getIsEnabled()));
        if (requestedConfig.isPresent()) {
            return requestedConfig.get().getModelName();
        }

        if (!strictWhitelist && enabledModels.isEmpty()) {
            log.warn("模型白名单未启用且无启用模型配置，沿用请求模型: {}", requested);
            return requested;
        }

        log.warn("请求模型 {} 不在启用白名单中，回退到默认模型 {}", requested, fallback);
        return fallback;
    }

    public ModelParameters resolveParameters(ChatRequest request, String modelName) {
        Double temperature = request == null ? null : request.getTemperature();
        Integer maxTokens = request == null ? null : request.getMaxTokens();
        String source = "request";

        if (temperature != null && (temperature < 0.0 || temperature > 1.0)) {
            log.warn("前端传递的 temperature 超出范围 [0.0, 1.0]: {}, 将忽略", temperature);
            temperature = null;
        }
        if (maxTokens != null && (maxTokens <= 0 || maxTokens > maxTokensCeiling)) {
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
                        maxTokens = Math.min(config.getMaxTokens(), maxTokensCeiling);
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

    private String resolveConfiguredDefaultModel() {
        try {
            return aiModelConfigService.getDefaultModel()
                    .filter(config -> Boolean.TRUE.equals(config.getIsEnabled()))
                    .map(ModelConfigDTO::getModelName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .orElse(configuredDefaultModel);
        } catch (Exception e) {
            log.warn("读取默认模型配置失败，使用配置默认模型: {}", e.getMessage());
            return configuredDefaultModel;
        }
    }

    private List<ModelConfigDTO> safeEnabledModels() {
        try {
            return aiModelConfigService.getEnabledModels();
        } catch (Exception e) {
            log.warn("读取启用模型白名单失败，使用默认模型兜底: {}", e.getMessage());
            return List.of();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ModelParameters(Double temperature, Integer maxTokens, String source) {
    }
}
