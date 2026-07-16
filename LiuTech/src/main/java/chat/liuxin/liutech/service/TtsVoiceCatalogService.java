package chat.liuxin.liutech.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * TTS 可用语音模型目录服务
 *
 * 说明：
 * - 基于 AI Hobbyist TTS 的 /models/v4 接口拉取可用语音模型
 * - 这里只做“模型名列表”收口，避免把后台做成推理参数实验台
 */
@Service
public class TtsVoiceCatalogService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1000))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> listVoiceModels(String baseUrl) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if (normalizedBaseUrl == null) {
            return List.of();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedBaseUrl + "/models/v4"))
                    .timeout(Duration.ofMillis(1500))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root.get("models");
            if (models == null || !models.isObject()) {
                return List.of();
            }

            List<String> result = new ArrayList<>();
            result.addAll(models.propertyNames());
            result.sort(String::compareTo);
            return result;
        } catch (Exception ignore) {
            return List.of();
        }
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
}
