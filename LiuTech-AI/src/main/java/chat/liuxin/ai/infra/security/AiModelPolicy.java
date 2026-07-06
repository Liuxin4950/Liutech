package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 模型选择与参数策略。
 *
 * 集中处理"前端请求某个模型"和"实际用哪个模型/什么参数"之间的映射：
 * - 校验请求的模型是否在启用白名单里，非法则回退默认模型
 * - 校验 temperature / maxTokens 是否合法，非法则从数据库配置读取或用默认值
 * - 严格白名单模式下，未启用的模型一律回退；关闭白名单且无启用模型时才放行请求
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelPolicy {

    private final AiModelConfigService aiModelConfigService;
    private final AiChatProperties aiChatProperties;

    /** 从聊天请求解析模型名（内部委托给字符串版本） */
    public String resolveModelName(ChatRequest request) {
        return resolveModelName(request == null ? null : request.getModel());
    }

    /**
     * 解析实际使用的模型名。
     *
     * 决策顺序：
     * 1. 请求为空 → 用配置的默认模型
     * 2. 请求命中启用白名单 → 用请求的模型
     * 3. 白名单未启用且启用模型列表为空 → 沿用请求的模型（宽松兜底）
     * 4. 其他情况 → 回退默认模型（记 warn 日志）
     */
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

        if (!aiChatProperties.getSecurity().isModelPolicyStrictWhitelist() && enabledModels.isEmpty()) {
            log.warn("模型白名单未启用且无启用模型配置，沿用请求模型: {}", requested);
            return requested;
        }

        log.warn("请求模型 {} 不在启用白名单中，回退到默认模型 {}", requested, fallback);
        return fallback;
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

    /** 安全读取启用模型列表；数据库不可用时返回空列表，避免抛异常影响聊天流程 */
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

    /** 解析后的模型参数三元组：温度、最大 token、参数来源（供日志观察） */
    public record ModelParameters(Double temperature, Integer maxTokens, String source) {
    }
}
