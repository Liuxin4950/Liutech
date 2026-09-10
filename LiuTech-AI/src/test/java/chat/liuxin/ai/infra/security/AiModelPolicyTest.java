package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.service.AiModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiModelPolicyTest {

    private AiModelConfigService modelConfigService;
    private AiModelPolicy policy;

    @BeforeEach
    void setUp() {
        modelConfigService = mock(AiModelConfigService.class);
        AiChatProperties props = new AiChatProperties();
        props.setDefaultModel("fallback-model");
        policy = new AiModelPolicy(modelConfigService, props, new PromptBudget(props));
    }

    @Test
    void shouldReturnDbDefaultModelWhenConfigured() {
        ModelConfigDTO defaultConfig = new ModelConfigDTO();
        defaultConfig.setModelName("db-default");
        defaultConfig.setIsEnabled(true);
        when(modelConfigService.getDefaultModel()).thenReturn(Optional.of(defaultConfig));

        assertEquals("db-default", policy.resolveModelName(new ChatRequest()));
    }

    @Test
    void shouldFallbackToYmlDefaultWhenNoDbDefault() {
        when(modelConfigService.getDefaultModel()).thenReturn(Optional.empty());

        assertEquals("fallback-model", policy.resolveModelName(new ChatRequest()));
    }

    @Test
    void shouldIgnoreInvalidTemperatureAndClampOversizedRequestMaxTokens() {
        ChatRequest request = new ChatRequest();
        request.setTemperature(2.0);
        request.setMaxTokens(999999);

        when(modelConfigService.getModelByName("fallback-model")).thenReturn(Optional.empty());

        AiModelPolicy.ModelParameters params = policy.resolveParameters(request, "fallback-model");

        // 越界温度直接忽略；越界输出上限被全局安全上限（默认 65536）夹住，而不是"整条忽略"
        assertNull(params.temperature());
        assertEquals(65536, params.maxTokens());
        assertTrue(params.outputClamped(), "应标记输出上限被夹小，供管理端/日志观察");
    }

    @Test
    void shouldPreferModelConfigOverRequestAndComputeInputBudget() {
        ChatRequest request = new ChatRequest();
        request.setTemperature(0.1);
        // 请求要更小的输出上限：允许（调用方要求短一点是合理诉求）
        request.setMaxTokens(1024);

        ModelConfigDTO config = new ModelConfigDTO();
        config.setModelName("glm-4.6");
        config.setIsEnabled(true);
        config.setMaxTokens(16384);
        config.setContextWindow(205000);
        config.setTemperature(new java.math.BigDecimal("0.90"));
        when(modelConfigService.getModelByName("glm-4.6")).thenReturn(Optional.of(config));

        AiModelPolicy.ModelParameters params = policy.resolveParameters(request, "glm-4.6");

        assertEquals(0.90, params.temperature(), 0.0001, "温度以模型配置为准");
        assertEquals(1024, params.maxTokens(), "请求更小的输出上限应被接受");
        assertEquals(205000, params.contextWindow());
        // 输入预算先按 上下文 205000 − 输出 1024 − 安全余量 512 = 203464 计算，
        // 再被全局成本护栏（默认 model-policy-max-input-tokens=96000）夹住：
        // 这是刻意设计——模型能力 205K ≠ 允许单次请求烧掉 205K 输入
        assertEquals(96000, params.inputBudgetTokens());
        assertTrue(params.inputCappedByPolicy());
    }

    @Test
    void shouldUseConfiguredModelOutputLimitAndIgnoreLargerRequest() {
        ChatRequest request = new ChatRequest();
        request.setMaxTokens(60000);

        ModelConfigDTO config = new ModelConfigDTO();
        config.setModelName("glm-4.6");
        config.setIsEnabled(true);
        config.setMaxTokens(16384);
        config.setContextWindow(205000);
        when(modelConfigService.getModelByName("glm-4.6")).thenReturn(Optional.of(config));

        AiModelPolicy.ModelParameters params = policy.resolveParameters(request, "glm-4.6");

        // 关键回归点：过去数据库里的 16384 会被全局 ceiling(8192) 静默夹成 8192，管理端"配了不生效"
        assertEquals(16384, params.maxTokens());
    }

    @Test
    void shouldFallbackToDefaultContextWindowWhenModelHasNoContextConfig() {
        ModelConfigDTO config = new ModelConfigDTO();
        config.setModelName("unknown-model");
        config.setIsEnabled(true);
        config.setMaxTokens(4096);
        when(modelConfigService.getModelByName("unknown-model")).thenReturn(Optional.of(config));

        AiModelPolicy.ModelParameters params = policy.resolveParameters(new ChatRequest(), "unknown-model");

        assertEquals(32768, params.contextWindow(), "未配置上下文窗口时用全局默认值兜底");
        // 32768 − 4096 − 512 = 28160，低于全局护栏 96000，因此不被夹
        assertEquals(32768 - 4096 - PromptBudget.SAFETY_MARGIN_TOKENS, params.inputBudgetTokens());
        assertFalse(params.inputCappedByPolicy());
    }

}