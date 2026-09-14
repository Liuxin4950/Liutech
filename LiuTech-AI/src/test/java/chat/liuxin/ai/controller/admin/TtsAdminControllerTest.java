package chat.liuxin.ai.controller.admin;

import chat.liuxin.ai.dto.tts.SiliconFlowVoiceDTO;
import chat.liuxin.ai.dto.tts.TtsConfigDTO;
import chat.liuxin.ai.dto.tts.TtsConfigRequest;
import chat.liuxin.ai.dto.tts.TtsSpeechDTO;
import chat.liuxin.ai.dto.tts.TtsSpeechRequest;
import chat.liuxin.ai.dto.tts.TtsStatusDTO;
import chat.liuxin.ai.service.tts.TtsConfigService;
import chat.liuxin.ai.service.tts.TtsSpeechService;
import chat.liuxin.ai.service.tts.TtsStatusService;
import chat.liuxin.ai.service.tts.TtsVoiceCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TtsAdminControllerTest {

    private TtsAdminController controller;
    private TtsConfigService ttsConfigService;
    private TtsStatusService ttsStatusService;
    private TtsVoiceCatalogService ttsVoiceCatalogService;
    private TtsSpeechService ttsSpeechService;

    @BeforeEach
    void setUp() {
        ttsConfigService = mock(TtsConfigService.class);
        ttsStatusService = mock(TtsStatusService.class);
        ttsVoiceCatalogService = mock(TtsVoiceCatalogService.class);
        ttsSpeechService = mock(TtsSpeechService.class);
        controller = new TtsAdminController(ttsConfigService, ttsStatusService, ttsVoiceCatalogService, ttsSpeechService);
    }

    // ========== getConfig ==========

    @Test
    void getConfig_shouldReturnConfig() {
        TtsConfigDTO config = new TtsConfigDTO();
        config.setEnabled(true);
        config.setBaseUrl("http://tts.local");
        config.setProvider("GPT_SOVITS");
        when(ttsConfigService.getConfig()).thenReturn(config);

        TtsConfigDTO result = controller.getConfig();

        assertTrue(result.getEnabled());
        assertEquals("http://tts.local", result.getBaseUrl());
    }

    @Test
    void getConfig_shouldReturnDisabledConfig() {
        TtsConfigDTO config = new TtsConfigDTO();
        config.setEnabled(false);
        when(ttsConfigService.getConfig()).thenReturn(config);

        TtsConfigDTO result = controller.getConfig();

        assertFalse(result.getEnabled());
    }

    // ========== updateConfig ==========

    @Test
    void updateConfig_shouldReturnSuccess() {
        TtsConfigRequest config = new TtsConfigRequest();
        config.setEnabled(true);
        TtsConfigDTO updated = new TtsConfigDTO();
        updated.setEnabled(true);
        when(ttsConfigService.updateConfig(config)).thenReturn(updated);

        TtsConfigDTO result = controller.updateConfig(config);

        assertTrue(result.getEnabled());
        verify(ttsConfigService).updateConfig(config);
        verify(ttsStatusService).clearCache();
    }

    @Test
    void updateConfig_shouldPropagateException() {
        TtsConfigRequest config = new TtsConfigRequest();
        doThrow(new RuntimeException("config error")).when(ttsConfigService).updateConfig(any());

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.updateConfig(config));
    }

    // ========== status ==========

    @Test
    void status_shouldReturnStatus() {
        TtsStatusDTO statusDto = new TtsStatusDTO();
        statusDto.setEnabled(true);
        statusDto.setOnline(true);
        when(ttsStatusService.getStatus()).thenReturn(statusDto);

        TtsStatusDTO result = controller.status();

        assertTrue(result.isEnabled());
        assertTrue(result.isOnline());
    }

    @Test
    void status_shouldReturnOfflineStatus() {
        TtsStatusDTO statusDto = new TtsStatusDTO();
        statusDto.setEnabled(true);
        statusDto.setOnline(false);
        when(ttsStatusService.getStatus()).thenReturn(statusDto);

        TtsStatusDTO result = controller.status();

        assertFalse(result.isOnline());
    }

    // ========== voices ==========

    @Test
    void voices_shouldReturnVoiceList() {
        TtsConfigDTO config = new TtsConfigDTO();
        config.setBaseUrl("http://tts.local");
        when(ttsConfigService.getConfig()).thenReturn(config);
        when(ttsVoiceCatalogService.listVoiceModels("http://tts.local")).thenReturn(List.of("voice1", "voice2"));

        List<String> result = controller.voices(null);

        assertEquals(2, result.size());
    }

    @Test
    void voices_shouldUseProvidedBaseUrl() {
        TtsConfigDTO config = new TtsConfigDTO();
        config.setBaseUrl("http://default.local");
        when(ttsConfigService.getConfig()).thenReturn(config);
        when(ttsVoiceCatalogService.listVoiceModels("http://custom.local")).thenReturn(List.of("voice1"));

        List<String> result = controller.voices("http://custom.local");

        assertEquals(1, result.size());
        verify(ttsVoiceCatalogService).listVoiceModels("http://custom.local");
    }

    // ========== siliconFlowVoices ==========

    @Test
    void siliconFlowVoices_shouldReturnVoiceList() {
        SiliconFlowVoiceDTO voice = SiliconFlowVoiceDTO.builder()
                .model("test-model")
                .customName("Test Voice")
                .build();
        when(ttsSpeechService.listSiliconFlowVoices()).thenReturn(List.of(voice));

        List<SiliconFlowVoiceDTO> result = controller.siliconFlowVoices();

        assertEquals(1, result.size());
        assertEquals("test-model", result.get(0).getModel());
    }

    @Test
    void siliconFlowVoices_shouldReturnEmptyList() {
        when(ttsSpeechService.listSiliconFlowVoices()).thenReturn(List.of());

        List<SiliconFlowVoiceDTO> result = controller.siliconFlowVoices();

        assertTrue(result.isEmpty());
    }

    // ========== testSpeech ==========

    @Test
    void testSpeech_shouldReturnAudioUrl() {
        TtsSpeechDTO response = TtsSpeechDTO.builder()
                .audioUrl("http://tts.local/audio.wav")
                .provider("GPT_SOVITS")
                .format("wav")
                .build();
        when(ttsSpeechService.synthesize("Hello")).thenReturn(response);

        TtsSpeechRequest request = new TtsSpeechRequest();
        request.setText("Hello");
        TtsSpeechDTO result = controller.testSpeech(request);

        assertEquals("http://tts.local/audio.wav", result.getAudioUrl());
    }

    @Test
    void testSpeech_shouldHandleException() {
        when(ttsSpeechService.synthesize(anyString())).thenThrow(new RuntimeException("tts error"));

        TtsSpeechRequest request = new TtsSpeechRequest();
        request.setText("Hello");
        // testSpeech does not have try-catch, so exception propagates
        assertThrows(RuntimeException.class, () -> controller.testSpeech(request));
    }
}
