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
        policy = new AiModelPolicy(modelConfigService, props);
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
    void shouldClampInvalidParameters() {
        ChatRequest request = new ChatRequest();
        request.setTemperature(2.0);
        request.setMaxTokens(999999);

        when(modelConfigService.getModelByName("fallback-model")).thenReturn(Optional.empty());

        AiModelPolicy.ModelParameters params = policy.resolveParameters(request, "fallback-model");

        assertNull(params.temperature());
        assertNull(params.maxTokens());
    }

}