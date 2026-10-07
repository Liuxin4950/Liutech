package chat.liuxin.ai.service.tts;

import chat.liuxin.ai.dto.tts.TtsConfigDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class TtsSpeechStatusTest {
    @TempDir Path tempDir;
    private TtsConfigDTO config;
    private HttpClient httpClient;
    private TtsSpeechService service;

    @BeforeEach
    void setUp() {
        TtsConfigService configService = mock(TtsConfigService.class);
        config = new TtsConfigDTO();
        config.setEnabled(true);
        config.setProvider("GPT_SOVITS");
        config.setBaseUrl("https://tts.example.invalid");
        config.setResponseFormat("mp3");
        when(configService.getConfig()).thenReturn(config);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("siliconflow.tts-api-key")).thenReturn("test-key");
        httpClient = mock(HttpClient.class);
        service = new TtsSpeechService(configService, environment, httpClient);
        ReflectionTestUtils.setField(service, "dotenvCache", Map.of());
        ReflectionTestUtils.setField(service, "siliconFlowBaseUrl", "https://siliconflow.example.invalid");
        ReflectionTestUtils.setField(service, "cacheDirectory", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxAgeHours", 24L);
        ReflectionTestUtils.setField(service, "maxBytes", 1_000_000L);
        ReflectionTestUtils.setField(service, "cleanupIntervalMs", 60_000L);
    }

    @Test
    void gpt503IsUnavailableButNormalPostOnly405IsReachable() throws Exception {
        reply(503, null);
        var unavailable = service.getStatus();
        assertTrue(unavailable.isConfigured());
        assertTrue(unavailable.isOnlineVerified());
        assertFalse(unavailable.isOnline());
        assertTrue(unavailable.getMessage().contains("503"));

        service.clearStatusCache();
        reply(405, null);
        assertTrue(service.getStatus().isOnline());
    }

    @Test
    void gptWrongEndpoint404IsNotOnline() throws Exception {
        reply(404, null);
        assertFalse(service.getStatus().isOnline());
    }

    @Test
    void configuredSiliconFlowMayAttemptSpeechWithoutPretendingAlreadyOnline() throws Exception {
        config.setProvider("SILICONFLOW");
        config.setSiliconFlowVoiceUri("speech:test");
        var initial = service.getStatus();
        assertTrue(initial.isConfigured());
        assertFalse(initial.isOnline());
        assertFalse(initial.isOnlineVerified());
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        reply(200, new byte[]{1, 2, 3});
        assertNotNull(service.inferSingleAudioUrl("测试语音"));
        var confirmed = service.getStatus();
        assertTrue(confirmed.isOnline());
        assertTrue(confirmed.isOnlineVerified());
    }

    @Test
    void siliconFlowFailureIsVisibleAndSubsequentSegmentsRespectCooldown() throws Exception {
        config.setProvider("SILICONFLOW");
        config.setSiliconFlowVoiceUri("speech:test");
        reply(503, new byte[0]);
        assertNull(service.inferSingleAudioUrl("第一段"));
        assertTrue(service.getStatus().isOnlineVerified());
        assertFalse(service.getStatus().isOnline());
        assertNull(service.inferSingleAudioUrl("第二段"));
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private void reply(int code, byte[] audio) throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(code);
        when(response.body()).thenReturn(audio);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
