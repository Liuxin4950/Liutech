package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.dto.SiliconFlowVoiceDTO;
import chat.liuxin.liutech.model.dto.TtsConfigDTO;
import chat.liuxin.liutech.model.dto.TtsSpeechRequestDTO;
import chat.liuxin.liutech.model.dto.TtsSpeechResponseDTO;
import chat.liuxin.liutech.model.dto.TtsStatusDTO;
import chat.liuxin.liutech.service.TtsConfigService;
import chat.liuxin.liutech.service.TtsSpeechService;
import chat.liuxin.liutech.service.TtsStatusService;
import chat.liuxin.liutech.service.TtsVoiceCatalogService;
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

        Result<TtsConfigDTO> result = controller.getConfig();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getEnabled());
        assertEquals("http://tts.local", result.getData().getBaseUrl());
    }

    @Test
    void getConfig_shouldReturnDisabledConfig() {
        TtsConfigDTO config = new TtsConfigDTO();
        config.setEnabled(false);
        when(ttsConfigService.getConfig()).thenReturn(config);

        Result<TtsConfigDTO> result = controller.getConfig();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertFalse(result.getData().getEnabled());
    }

    // ========== updateConfig ==========

    @Test
    void updateConfig_shouldReturnSuccess() {
        TtsConfigDTO config = new TtsConfigDTO();
        config.setEnabled(true);

        Result<String> result = controller.updateConfig(config);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("更新成功", result.getData());
        verify(ttsConfigService).updateConfig(config);
        verify(ttsStatusService).clearCache();
    }

    @Test
    void updateConfig_shouldPropagateException() {
        TtsConfigDTO config = new TtsConfigDTO();
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

        Result<TtsStatusDTO> result = controller.status();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEnabled());
        assertTrue(result.getData().isOnline());
    }

    @Test
    void status_shouldReturnOfflineStatus() {
        TtsStatusDTO statusDto = new TtsStatusDTO();
        statusDto.setEnabled(true);
        statusDto.setOnline(false);
        when(ttsStatusService.getStatus()).thenReturn(statusDto);

        Result<TtsStatusDTO> result = controller.status();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertFalse(result.getData().isOnline());
    }

    // ========== voices ==========

    @Test
    void voices_shouldReturnVoiceList() {
        TtsConfigDTO config = new TtsConfigDTO();
        config.setBaseUrl("http://tts.local");
        when(ttsConfigService.getConfig()).thenReturn(config);
        when(ttsVoiceCatalogService.listVoiceModels("http://tts.local")).thenReturn(List.of("voice1", "voice2"));

        Result<List<String>> result = controller.voices(null);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(2, result.getData().size());
    }

    @Test
    void voices_shouldUseProvidedBaseUrl() {
        TtsConfigDTO config = new TtsConfigDTO();
        config.setBaseUrl("http://default.local");
        when(ttsConfigService.getConfig()).thenReturn(config);
        when(ttsVoiceCatalogService.listVoiceModels("http://custom.local")).thenReturn(List.of("voice1"));

        Result<List<String>> result = controller.voices("http://custom.local");

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
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

        Result<List<SiliconFlowVoiceDTO>> result = controller.siliconFlowVoices();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("test-model", result.getData().get(0).getModel());
    }

    @Test
    void siliconFlowVoices_shouldReturnEmptyList() {
        when(ttsSpeechService.listSiliconFlowVoices()).thenReturn(List.of());

        Result<List<SiliconFlowVoiceDTO>> result = controller.siliconFlowVoices();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== testSpeech ==========

    @Test
    void testSpeech_shouldReturnAudioUrl() {
        TtsSpeechResponseDTO response = TtsSpeechResponseDTO.builder()
                .audioUrl("http://tts.local/audio.wav")
                .provider("GPT_SOVITS")
                .format("wav")
                .build();
        when(ttsSpeechService.synthesize("Hello")).thenReturn(response);

        TtsSpeechRequestDTO request = new TtsSpeechRequestDTO();
        request.setText("Hello");
        Result<TtsSpeechResponseDTO> result = controller.testSpeech(request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("http://tts.local/audio.wav", result.getData().getAudioUrl());
    }

    @Test
    void testSpeech_shouldHandleException() {
        when(ttsSpeechService.synthesize(anyString())).thenThrow(new RuntimeException("tts error"));

        TtsSpeechRequestDTO request = new TtsSpeechRequestDTO();
        request.setText("Hello");
        // testSpeech does not have try-catch, so exception propagates
        assertThrows(RuntimeException.class, () -> controller.testSpeech(request));
    }
}
