package chat.liuxin.ai.common.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    public TtsClient(@Qualifier("aiHttpClient") HttpClient aiHttpClient) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(aiHttpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 读取应用配置里的并发上限,重建信号量。
     *
     * 默认 1 是为了保护 GPT-SoVITS 单卡推理资源,配置更高时按实际能力放开。
     */
    @PostConstruct
    void initConcurrencyLimit() {
        int permits = Math.max(1, proxyConcurrency);
        ttsSemaphore = new Semaphore(permits);
        log.info("TTS proxy concurrency limit initialized: {}", permits);
    }

    /**
     * 从主服务 /tts/status 获取 TTS 全局开关、在线状态、当前引擎等信息。
     *
     * 结果按 CACHE_TTL_MS 短期缓存。因为一次流式回复会切多段并多次触发推理,
     * 每段都请求 /tts/status 会造成不必要的 HTTP 抖动;短缓存兼顾"动态生效"和成本。
     * 请求失败时返回 enabled=false / online=false,message 为异常类型简名,调用方视为不可用。
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
     * 通过主后端代理生成单段 TTS 音频,返回前端可直接播放的音频地址。
     *
     * AI 服务不直连 GPT-SoVITS / SiliconFlow,由主后端 /tts/speech 代理推理并把结果缓存为 /tts/audio/**。
     * 请求头带 X-TTS-Internal-Token 走跨服务内部校验(值来自 TTS_PROXY_INTERNAL_TOKEN)。
     * 用信号量限制并发(默认 1),保护笔记本推理资源。
     * 返回 null 表示 TTS 未启用/未在线/推理失败,调用方应发 audio-skip 事件让前端跳过该段。
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
            log.warn("TTS推理失败: {}", e.getMessage(), e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }

        return null;
    }

    /**
     * 归一化主后端返回的音频地址:
     * 完整 URL 原样返回,以 / 开头的绝对路径保持不变,相对路径补前导 /,空串返回 null。
     * 保证前端拿到的是可直接放入 audio.src 的路径。
     */
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

    /**
     * 去掉配置里 backend URL 尾部多余的 /,拼接时避免出现 //。
     */
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
