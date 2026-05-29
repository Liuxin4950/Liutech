package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.service.AiModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiModelPolicyTest {

    private AiModelConfigService modelConfigService;
    private AiModelPolicy policy;

    @BeforeEach
    void setUp() throws Exception {
        modelConfigService = mock(AiModelConfigService.class);
        policy = new AiModelPolicy(modelConfigService);
        setField(policy, "configuredDefaultModel", "fallback-model");
        setField(policy, "strictWhitelist", true);
        setField(policy, "maxTokensCeiling", 4096);
    }

    @Test
    void shouldFallbackToDefaultWhenNoWhitelistConfigured() {
        when(modelConfigService.getDefaultModel()).thenReturn(Optional.empty());
        when(modelConfigService.getEnabledModels()).thenReturn(Collections.emptyList());
        when(modelConfigService.getModelByName("unknown-model")).thenReturn(Optional.empty());

        assertEquals("fallback-model", policy.resolveModelName("unknown-model"));
    }

    @Test
    void shouldAllowEnabledRequestedModel() {
        ModelConfigDTO config = new ModelConfigDTO();
        config.setModelName("allowed-model");
        config.setIsEnabled(true);

        when(modelConfigService.getDefaultModel()).thenReturn(Optional.empty());
        when(modelConfigService.getEnabledModels()).thenReturn(List.of(config));
        when(modelConfigService.getModelByName("allowed-model")).thenReturn(Optional.of(config));

        assertEquals("allowed-model", policy.resolveModelName("allowed-model"));
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

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
