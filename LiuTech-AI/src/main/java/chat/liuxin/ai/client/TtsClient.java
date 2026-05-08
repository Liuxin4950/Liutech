package chat.liuxin.ai.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TtsClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${blog.api.url:http://backend:8080}")
    private String backendApiUrl;

    @Value("${tts.proxy.internal-token:${TTS_PROXY_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Value("${tts.proxy.concurrency:${TTS_PROXY_CONCURRENCY:1}}")
    private int proxyConcurrency;

    private volatile TtsStatus cachedStatus;
    private volatile long cachedAt = 0L;
    private volatile Semaphore ttsSemaphore = new Semaphore(1);

    /**
     * 主服务 TTS 状态缓存时间（毫秒）
     *
     * 设计动机：
     * - 一次流式回复可能切分出多个语音片段，会多次触发 infer_single
     * - 若每次 infer_single 都去请求主服务 /tts/status，会产生不必要的额外 HTTP 开销
     * - 这里做短期缓存即可满足“能动态更新配置”的需求
     */
    private static final long CACHE_TTL_MS = 5000L;

    public TtsClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    void initConcurrencyLimit() {
        int permits = Math.max(1, proxyConcurrency);
        ttsSemaphore = new Semaphore(permits);
        log.info("TTS proxy concurrency limit initialized: {}", permits);
    }

    /**
     * 从主服务获取 TTS 配置与在线状态（带短期缓存）
     *
     * 返回内容包含：
     * - enabled：管理端全局开关
     * - online：主服务探测结果
     * - provider：当前主服务代理的 TTS 引擎
     */
    public TtsStatus getStatus() {
        long now = System.currentTimeMillis();
        TtsStatus hit = cachedStatus;
        if (hit != null && (now - cachedAt) <= CACHE_TTL_MS) {
            return hit;
        }

        TtsStatus status = new TtsStatus();
        status.setEnabled(false);
        status.setOnline(false);

        try {
            String url = normalizeBaseUrl(backendApiUrl) + "/tts/status";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                status.setEnabled(data.has("enabled") && data.get("enabled").asBoolean(false));
                status.setOnline(data.has("online") && data.get("online").asBoolean(false));
                status.setBaseUrl(data.has("baseUrl") && !data.get("baseUrl").isNull() ? data.get("baseUrl").asText() : null);
                status.setVoiceModel(data.has("voiceModel") && !data.get("voiceModel").isNull() ? data.get("voiceModel").asText() : null);
                status.setProvider(data.has("provider") && !data.get("provider").isNull() ? data.get("provider").asText() : null);
                status.setMessage(data.has("message") && !data.get("message").isNull() ? data.get("message").asText() : null);
            }
        } catch (Exception e) {
            status.setMessage(e.getClass().getSimpleName());
        }

        cachedStatus = status;
        cachedAt = now;
        return status;
    }

    /**
     * 通过主后端代理生成单段音频，并返回前端可播放的同源音频地址。
     *
     * 关键点：
     * - AI 服务不直连 GPT-SoVITS 或 SiliconFlow
     * - 音频必须由主后端缓存为 /tts/audio/** 后再给前端播放
     */
    public String inferSingleAudioUrl(String text) {
        if (text == null || text.isBlank()) return null;

        TtsStatus status = getStatus();
        if (!status.isEnabled() || !status.isOnline()) return null;

        boolean acquired = false;
        Semaphore semaphore = ttsSemaphore;
        try {
            semaphore.acquire();
            acquired = true;

            Map<String, Object> body = new HashMap<>();
            body.put("text", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set("X-TTS-Internal-Token", internalToken.trim());
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    normalizeBaseUrl(backendApiUrl) + "/tts/speech",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                String audioUrl = data.has("audioUrl") && !data.get("audioUrl").isNull() ? data.get("audioUrl").asText() : null;
                return normalizeBackendAudioUrl(audioUrl);
            }
        } catch (Exception e) {
            log.warn("TTS推理失败: {}", e.getMessage());
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }

        return null;
    }

    private String normalizeBackendAudioUrl(String audioUrl) {
        if (audioUrl == null) return null;
        String u = audioUrl.trim();
        if (u.isEmpty()) return null;
        if (u.startsWith("http://") || u.startsWith("https://")) {
            return u;
        }
        if (u.startsWith("/")) {
            return u;
        }
        return "/" + u;
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    @Data
    public static class TtsStatus {
        private boolean enabled;
        private boolean online;
        private String baseUrl;
        private String voiceModel;
        private String provider;
        private String message;
    }
}
