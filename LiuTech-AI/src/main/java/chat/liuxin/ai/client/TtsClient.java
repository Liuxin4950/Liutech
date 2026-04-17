package chat.liuxin.ai.client;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TtsClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${blog.api.url:http://backend:8080}")
    private String backendApiUrl;

    @Value("${tts.infer.default-model-name:原神-中文-纳西妲_ZH}")
    private String defaultModelName;

    private volatile TtsStatus cachedStatus;
    private volatile long cachedAt = 0L;

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

    /**
     * 从主服务获取 TTS 配置与在线状态（带短期缓存）
     *
     * 返回内容包含：
     * - enabled：管理端全局开关
     * - online：主服务探测结果
     * - baseUrl：TTS 对外可访问的基础地址（建议为内网穿透/局域网地址）
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
            String url = backendApiUrl + "/tts/status";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                status.setEnabled(data.has("enabled") && data.get("enabled").asBoolean(false));
                status.setOnline(data.has("online") && data.get("online").asBoolean(false));
                status.setBaseUrl(data.has("baseUrl") && !data.get("baseUrl").isNull() ? data.get("baseUrl").asText() : null);
                status.setVoiceModel(data.has("voiceModel") && !data.get("voiceModel").isNull() ? data.get("voiceModel").asText() : null);
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
     * 调用 TTS 服务生成单段音频，并返回“前端可直接访问”的音频地址
     *
     * 关键点：
     * - 音频地址可能被 TTS 服务返回为 localhost/127.0.0.1
     * - 为避免云端网页/其他设备无法访问，这里会用 baseUrl 做归一化/改写
     */
    public String inferSingleAudioUrl(String text) {
        if (text == null || text.isBlank()) return null;

        TtsStatus status = getStatus();
        if (!status.isEnabled() || !status.isOnline()) return null;
        if (status.getBaseUrl() == null || status.getBaseUrl().isBlank()) return null;

        String baseUrl = normalizeBaseUrl(status.getBaseUrl());
        if (baseUrl == null) return null;

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("dl_url", baseUrl);
            body.put("version", "v4");
            body.put("model_name", resolveVoiceModel(status));
            body.put("prompt_text_lang", "中文");
            body.put("emotion", "默认");
            body.put("text", text);
            body.put("text_lang", "中文");
            body.put("top_k", 10);
            body.put("top_p", 1);
            body.put("temperature", 1);
            body.put("text_split_method", "按标点符号切");
            body.put("batch_size", 10);
            body.put("batch_threshold", 0.75);
            body.put("split_bucket", true);
            body.put("speed_facter", 1);
            body.put("fragment_interval", 0.3);
            body.put("media_type", "wav");
            body.put("parallel_infer", false);
            body.put("repetition_penalty", 1.35);
            body.put("seed", -1);
            body.put("sample_steps", 16);
            body.put("if_sr", false);

            String response = restTemplate.postForObject(baseUrl + "/infer_single", body, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.has("audio_url") && !root.get("audio_url").isNull()) {
                String audioUrl = root.get("audio_url").asText();
                return normalizeAudioUrl(audioUrl, baseUrl);
            }
        } catch (Exception e) {
            log.warn("TTS推理失败: {}", e.getMessage());
        }

        return null;
    }

    private String normalizeAudioUrl(String audioUrl, String baseUrl) {
        // 归一化规则：
        // 1) 相对路径：拼接 baseUrl
        // 2) 非 http(s) 且非以 / 开头：拼接 baseUrl + "/" + url
        // 3) 若是 http(s) 但 host 为 localhost/127.0.0.1：替换为 baseUrl 的 host
        if (audioUrl == null) return null;
        String u = audioUrl.trim();
        if (u.isEmpty()) return null;

        String base = normalizeBaseUrl(baseUrl);
        if (base == null) return u;

        try {
            if (u.startsWith("/")) {
                return base + u;
            }
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                return base + "/" + u;
            }

            URI raw = URI.create(u);
            String host = raw.getHost();
            String path = raw.getPath();
            String query = raw.getQuery();

            boolean isLocalHost = host != null && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host));
            if (isLocalHost && path != null && !path.isBlank()) {
                return base + path + (query != null && !query.isBlank() ? ("?" + query) : "");
            }
        } catch (Exception ignore) {
        }

        return u;
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

    private String resolveVoiceModel(TtsStatus status) {
        if (status != null && status.getVoiceModel() != null && !status.getVoiceModel().isBlank()) {
            return status.getVoiceModel().trim();
        }
        return defaultModelName;
    }

    @Data
    public static class TtsStatus {
        private boolean enabled;
        private boolean online;
        private String baseUrl;
        private String voiceModel;
        private String message;
    }
}
