package chat.liuxin.ai.service.tts;

import chat.liuxin.ai.dto.tts.TtsConfigDTO;
import chat.liuxin.ai.dto.tts.TtsConfigRequest;
import chat.liuxin.ai.entity.AiTtsConfig;
import chat.liuxin.ai.mapper.AiTtsConfigMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.web.server.ResponseStatusException;

class TtsConfigServiceTest {

    @Test
    void rawPcmIsRejectedWithoutPersistingUnsupportedBrowserFormat() {
        AiTtsConfigMapper mapper = mock(AiTtsConfigMapper.class);
        TtsConfigRequest request = new TtsConfigRequest();
        request.setResponseFormat("pcm");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new TtsConfigService(mapper).updateConfig(request));
        assertEquals(400, error.getStatusCode().value());
        verify(mapper, never()).updateById(any(AiTtsConfig.class));
    }

    @Test
    void legacyPcmConfigFallsBackToPlayableMp3() {
        AiTtsConfigMapper mapper = mock(AiTtsConfigMapper.class);
        AiTtsConfig entity = new AiTtsConfig();
        entity.setId(1L);
        entity.setResponseFormat("pcm");
        when(mapper.selectById(1L)).thenReturn(entity);
        assertEquals("mp3", new TtsConfigService(mapper).getConfig().getResponseFormat());
    }

    @Test
    void shouldReadTypedAiOwnedConfig() {
        AiTtsConfigMapper mapper = mock(AiTtsConfigMapper.class);
        AiTtsConfig entity = new AiTtsConfig();
        entity.setId(1L);
        entity.setEnabled(true);
        entity.setProvider("SILICON_FLOW");
        entity.setSiliconFlowModel("model");
        entity.setResponseFormat("wav");
        entity.setSampleRate(24000);
        entity.setSpeed(new BigDecimal("1.25"));
        when(mapper.selectById(1L)).thenReturn(entity);

        TtsConfigDTO dto = new TtsConfigService(mapper).getConfig();

        assertTrue(dto.getEnabled());
        assertEquals("SILICONFLOW", dto.getProvider());
        assertEquals(24000, dto.getSampleRate());
        assertEquals(1.25, dto.getSpeed());
    }

    @Test
    void updateShouldClampSpeedAndPersistSingleRow() {
        AiTtsConfigMapper mapper = mock(AiTtsConfigMapper.class);
        AiTtsConfig entity = new AiTtsConfig();
        entity.setId(1L);
        when(mapper.selectById(1L)).thenReturn(entity);
        TtsConfigRequest request = new TtsConfigRequest();
        request.setEnabled(true);
        request.setSpeed(9.0);

        TtsConfigDTO updated = new TtsConfigService(mapper).updateConfig(request);

        assertEquals(4.0, updated.getSpeed());
        verify(mapper).updateById(entity);
    }
}
