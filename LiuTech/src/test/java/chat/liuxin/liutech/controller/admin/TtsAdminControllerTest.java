package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.TtsConfigReq;
import chat.liuxin.liutech.req.TtsSpeechReq;
import chat.liuxin.liutech.resp.SiliconFlowVoiceResp;
import chat.liuxin.liutech.resp.TtsConfigResp;
import chat.liuxin.liutech.resp.TtsSpeechResp;
import chat.liuxin.liutech.resp.TtsStatusResp;
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
        TtsConfigResp config = new TtsConfigResp();
        config.setEnabled(true);
        config.setBaseUrl("http://tts.local");
        config.setProvider("GPT_SOVITS");
        when(ttsConfigService.getConfig()).thenReturn(config);

        Result<TtsConfigResp> result = controller.getConfig();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getEnabled());
        assertEquals("http://tts.local", result.getData().getBaseUrl());
    }

    @Test
    void getConfig_shouldReturnDisabledConfig() {
        TtsConfigResp config = new TtsConfigResp();
        config.setEnabled(false);
        when(ttsConfigService.getConfig()).thenReturn(config);

        Result<TtsConfigResp> result = controller.getConfig();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertFalse(result.getData().getEnabled());
    }

    // ========== updateConfig ==========

    @Test
    void updateConfig_shouldReturnSuccess() {
        TtsConfigReq config = new TtsConfigReq();
        config.setEnabled(true);

        Result<String> result = controller.updateConfig(config);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("更新成功", result.getData());
        verify(ttsConfigService).updateConfig(config);
        verify(ttsStatusService).clearCache();
    }

    @Test
    void updateConfig_shouldPropagateException() {
        TtsConfigReq config = new TtsConfigReq();
        doThrow(new RuntimeException("config error")).when(ttsConfigService).updateConfig(any());

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.updateConfig(config));
    }

    // ========== status ==========

    @Test
    void status_shouldReturnStatus() {
        TtsStatusResp statusDto = new TtsStatusResp();
        statusDto.setEnabled(true);
        statusDto.setOnline(true);
        when(ttsStatusService.getStatus()).thenReturn(statusDto);

        Result<TtsStatusResp> result = controller.status();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEnabled());
        assertTrue(result.getData().isOnline());
    }

    @Test
    void status_shouldReturnOfflineStatus() {
        TtsStatusResp statusDto = new TtsStatusResp();
        statusDto.setEnabled(true);
        statusDto.setOnline(false);
        when(ttsStatusService.getStatus()).thenReturn(statusDto);

        Result<TtsStatusResp> result = controller.status();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertFalse(result.getData().isOnline());
    }

    // ========== voices ==========

    @Test
    void voices_shouldReturnVoiceList() {
        TtsConfigResp config = new TtsConfigResp();
        config.setBaseUrl("http://tts.local");
        when(ttsConfigService.getConfig()).thenReturn(config);
        when(ttsVoiceCatalogService.listVoiceModels("http://tts.local")).thenReturn(List.of("voice1", "voice2"));

        Result<List<String>> result = controller.voices(null);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(2, result.getData().size());
    }

    @Test
    void voices_shouldUseProvidedBaseUrl() {
        TtsConfigResp config = new TtsConfigResp();
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
        SiliconFlowVoiceResp voice = SiliconFlowVoiceResp.builder()
                .model("test-model")
                .customName("Test Voice")
                .build();
        when(ttsSpeechService.listSiliconFlowVoices()).thenReturn(List.of(voice));

        Result<List<SiliconFlowVoiceResp>> result = controller.siliconFlowVoices();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("test-model", result.getData().get(0).getModel());
    }

    @Test
    void siliconFlowVoices_shouldReturnEmptyList() {
        when(ttsSpeechService.listSiliconFlowVoices()).thenReturn(List.of());

        Result<List<SiliconFlowVoiceResp>> result = controller.siliconFlowVoices();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== testSpeech ==========

    @Test
    void testSpeech_shouldReturnAudioUrl() {
        TtsSpeechResp response = TtsSpeechResp.builder()
                .audioUrl("http://tts.local/audio.wav")
                .provider("GPT_SOVITS")
                .format("wav")
                .build();
        when(ttsSpeechService.synthesize("Hello")).thenReturn(response);

        TtsSpeechReq request = new TtsSpeechReq();
        request.setText("Hello");
        Result<TtsSpeechResp> result = controller.testSpeech(request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("http://tts.local/audio.wav", result.getData().getAudioUrl());
    }

    @Test
    void testSpeech_shouldHandleException() {
        when(ttsSpeechService.synthesize(anyString())).thenThrow(new RuntimeException("tts error"));

        TtsSpeechReq request = new TtsSpeechReq();
        request.setText("Hello");
        // testSpeech does not have try-catch, so exception propagates
        assertThrows(RuntimeException.class, () -> controller.testSpeech(request));
    }
}
