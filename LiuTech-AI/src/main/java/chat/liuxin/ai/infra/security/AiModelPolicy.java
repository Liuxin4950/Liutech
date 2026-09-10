package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * AI 模型选择与参数策略。
 *
 * <p><b>模型选择</b>：出于额度安全考虑，模型完全由服务端决定（数据库配置的默认模型，
 * 未配置时回退 application.yml 默认值），前端不参与模型选择，避免接口被换模型消耗额度。
 *
 * <p><b>参数策略</b>：以管理端的模型配置为准，解析出一次请求实际使用的
 * temperature / 输出上限 / 上下文窗口 / 输入预算。请求参数只能"更保守"，不能突破模型配置：
 * <ul>
 *   <li>请求带了更小的 maxTokens → 取更小的（调用方要求"短一点"是合理诉求）；</li>
 *   <li>请求带了更大的 maxTokens → 以模型配置为准，并打 WARN 说明被收敛；</li>
 *   <li>管理端配置超过全局安全上限 → 保存时就报错；万一 yml 事后被调小，读取时夹小并打 WARN。</li>
 * </ul>
 * 历史上这里用全局 {@code model-policy-max-tokens-ceiling} 静默夹取数据库配置，
 * 导致管理端填 16384 却始终按 8192 生效、且毫无提示 —— 这正是"配了不生效"的来源。
 *
 * @author 刘鑫
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelPolicy {

    private final AiModelConfigService aiModelConfigService;
    private final AiChatProperties aiChatProperties;
    private final PromptBudget promptBudget;

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
     * 解析实际使用的 temperature / 输出上限 / 上下文窗口 / 输入预算。
     *
     * @param request   原始请求（可携带 temperature / maxTokens，只允许比模型配置更小）
     * @param modelName 已解析出的模型名
     * @return 生效参数；source 标记参数来源（database/request/default…），仅用于日志观察
     */
    public ModelParameters resolveParameters(ChatRequest request, String modelName) {
        Double requestTemperature = request == null ? null : request.getTemperature();
        Integer requestMaxTokens = request == null ? null : request.getMaxTokens();

        if (requestTemperature != null && (requestTemperature < 0.0 || requestTemperature > 1.0)) {
            log.warn("请求携带的 temperature 超出范围 [0.0, 1.0]: {}, 已忽略", requestTemperature);
            requestTemperature = null;
        }
        if (requestMaxTokens != null && requestMaxTokens <= 0) {
            log.warn("请求携带的 maxTokens 非法: {}, 已忽略", requestMaxTokens);
            requestMaxTokens = null;
        }

        // 读取管理端的模型配置（上下文窗口 / 输出上限 / 温度都来自这里）
        ModelConfigDTO config = null;
        try {
            config = aiModelConfigService.getModelByName(modelName)
                    .filter(item -> Boolean.TRUE.equals(item.getIsEnabled()))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("读取模型参数失败，模型: {}, 错误: {}", modelName, e.getMessage());
        }

        String source = config != null ? "database" : "default";
        Integer configuredMaxTokens = config != null ? config.getMaxTokens() : null;
        Integer configuredContextWindow = config != null ? config.getContextWindow() : null;
        Double configuredTemperature = config != null && config.getTemperature() != null
                ? config.getTemperature().doubleValue()
                : null;

        // 温度：模型配置优先，行为可预期；配置缺失时才用请求值
        Double temperature = configuredTemperature != null ? configuredTemperature : requestTemperature;
        if (configuredTemperature != null && requestTemperature != null
                && !configuredTemperature.equals(requestTemperature)) {
            log.debug("忽略请求 temperature {}，按模型配置 {} 生效", requestTemperature, configuredTemperature);
            source = source + "+request-ignored";
        }

        // 输出上限：请求可以要求更小，但不得超过模型配置
        Integer maxTokens = configuredMaxTokens;
        if (maxTokens == null) {
            maxTokens = requestMaxTokens;
        } else if (requestMaxTokens != null && requestMaxTokens < maxTokens) {
            maxTokens = requestMaxTokens;
            source = source + "+request-smaller";
        } else if (requestMaxTokens != null && requestMaxTokens > maxTokens) {
            log.warn("请求 maxTokens {} 超过模型「{}」配置的 {}，按配置生效",
                    requestMaxTokens, modelName, configuredMaxTokens);
            source = source + "+request-clamped";
        }

        PromptBudget.ModelLimits limits = promptBudget.resolveLimits(maxTokens, configuredContextWindow);

        return new ModelParameters(
                temperature,
                limits.maxOutputTokens(),
                limits.contextWindow(),
                limits.inputBudgetTokens(),
                limits.outputClamped(),
                limits.inputCappedByPolicy(),
                source);
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

    /**
     * 解析后的模型参数。
     *
     * @param temperature        生效温度
     * @param maxTokens          生效输出上限
     * @param contextWindow      生效上下文窗口（输入 + 输出总上限）
     * @param inputBudgetTokens  生效输入预算（历史 + 上下文注入 + 工具结果 + 当前输入都要装进这里）
     * @param outputClamped      输出上限是否被全局安全上限夹小
     * @param inputCappedByPolicy 输入预算是否被全局成本护栏夹小
     * @param source             参数来源，供日志观察
     */
    public record ModelParameters(Double temperature, Integer maxTokens, int contextWindow,
                                  int inputBudgetTokens, boolean outputClamped, boolean inputCappedByPolicy,
                                  String source) {

        /**
         * 换一个温度（写作模式在模型未配置温度时用 0.3 兜底）。
         *
         * 只换温度、不动上限：输出上限与上下文窗口一律以模型配置为准，
         * 避免又出现"某条链路偷偷覆盖了管理端配置"的情况。
         *
         * @param newTemperature 新温度
         */
        public ModelParameters withTemperature(Double newTemperature) {
            return new ModelParameters(newTemperature, maxTokens, contextWindow,
                    inputBudgetTokens, outputClamped, inputCappedByPolicy, source + "+writing-default-temp");
        }
    }
}
