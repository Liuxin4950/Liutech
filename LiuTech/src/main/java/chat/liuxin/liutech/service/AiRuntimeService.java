package chat.liuxin.liutech.service;

import org.springframework.beans.factory.annotation.Value;
import chat.liuxin.liutech.resp.AiRuntimeResp;
import chat.liuxin.liutech.resp.TtsPublicStatusResp;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AI 运行时聚合服务
 */
@Service
public class AiRuntimeService {

    private final TtsSpeechService ttsSpeechService;

    @Value("${ai.service.url:${AI_SERVICE_URL:http://ai:8081}}")
    private String aiServiceUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1000))
            .build();

    public AiRuntimeService(TtsSpeechService ttsSpeechService) {
        this.ttsSpeechService = ttsSpeechService;
    }

    public AiRuntimeResp getRuntime() {
        AiRuntimeResp dto = new AiRuntimeResp();
        dto.setTts(TtsPublicStatusResp.from(ttsSpeechService.getStatus()));

        try {
            String normalizedBaseUrl = normalizeBaseUrl(aiServiceUrl);
            if (normalizedBaseUrl == null) {
                dto.setAiOnline(false);
                dto.setAiMessage("离线：AI 服务地址未配置");
                return dto;
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedBaseUrl + "/ai/models/default"))
                    .timeout(Duration.ofMillis(1500))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                dto.setAiOnline(true);
                dto.setAiMessage("在线");
                dto.setDefaultModel(stripJsonString(response.body()));
            } else {
                dto.setAiOnline(false);
                dto.setAiMessage("离线（HTTP " + response.statusCode() + "）");
            }
        } catch (Exception e) {
            dto.setAiOnline(false);
            dto.setAiMessage("离线：" + e.getClass().getSimpleName());
        }

        return dto;
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? null : s;
    }

    private String stripJsonString(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
