package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.model.dto.SiliconFlowVoiceDTO;
import chat.liuxin.liutech.model.dto.TtsConfigDTO;
import chat.liuxin.liutech.model.dto.TtsSpeechResponseDTO;
import chat.liuxin.liutech.model.dto.TtsStatusDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 统一 TTS 推理、缓存、状态探测和 SiliconFlow 音色管理。
 *
 * 合并自：TtsCacheManager、TtsStatusService、SiliconFlowKeyResolver
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsSpeechService {

    private final TtsConfigService ttsConfigService;
    private final FileUploadConfig fileUploadConfig;
    private final Environment environment;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    // ---- SiliconFlow 配置 ----
    @Value("${siliconflow.base-url:https://api.siliconflow.cn}")
    private String siliconFlowBaseUrl;

    // ---- 缓存配置 ----
    @Value("${tts.cache.max-age-hours:${TTS_CACHE_MAX_AGE_HOURS:24}}")
    private long maxAgeHours;
    @Value("${tts.cache.max-bytes:${TTS_CACHE_MAX_BYTES:536870912}}")
    private long maxBytes;
    @Value("${tts.cache.cleanup-interval-ms:${TTS_CACHE_CLEANUP_INTERVAL_MS:3600000}}")
    private long cleanupIntervalMs;

    // ---- 缓存节流 ----
    private final AtomicLong lastCleanupAt = new AtomicLong(0L);

    // ---- 状态缓存 ----
    private static final long STATUS_CACHE_TTL_MS = 5000L;
    private final AtomicReference<TtsStatusDTO> statusCache = new AtomicReference<>();
    private final HttpClient statusHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(800))
            .build();

    // ---- SiliconFlow Key 缓存 ----
    private static final String SOURCE_TTS = "SILICONFLOW_TTS_API_KEY";
    private static final String SOURCE_COMPAT = "SILICONFLOW_API_KEY";
    private static final String SOURCE_AI = "SPRING_AI_OPENAI_API_KEY";
    private volatile Map<String, String> dotenvCache;

    // ==================== TTS 合成 ====================

    public TtsSpeechResponseDTO synthesize(String text) {
        String normalizedText = normalizeText(text);
        if (normalizedText == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "语音文本不能为空");
        }

        TtsConfigDTO config = ttsConfigService.getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "语音功能已关闭");
        }

        if (TtsConfigService.PROVIDER_SILICONFLOW.equals(config.getProvider())) {
            return synthesizeWithSiliconFlow(config, normalizedText);
        }
        return synthesizeWithGptSovits(config, normalizedText);
    }

    public Path resolveAudioFile(String fileName) {
        if (fileName == null || !fileName.matches("[a-f0-9\\-]{36}\\.(mp3|wav|opus|pcm)")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音频文件不存在");
        }
        Path path = cacheDir().resolve(fileName).normalize();
        if (!path.startsWith(cacheDir()) || !Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音频文件不存在");
        }
        return path;
    }

    public MediaType mediaTypeFor(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) return MediaType.parseMediaType("audio/mpeg");
        if (lower.endsWith(".wav")) return MediaType.parseMediaType("audio/wav");
        if (lower.endsWith(".opus")) return MediaType.parseMediaType("audio/ogg");
        if (lower.endsWith(".pcm")) return MediaType.APPLICATION_OCTET_STREAM;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    // ==================== SiliconFlow 音色管理 ====================

    public SiliconFlowVoiceDTO uploadSiliconFlowVoice(MultipartFile file, String model, String customName, String text) {
        if (!hasTtsApiKey()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未配置 SiliconFlow API Key");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参考音频不能为空");
        }
        String normalizedModel = defaultText(model, TtsConfigService.DEFAULT_SILICON_FLOW_MODEL);
        String normalizedName = normalizeText(customName);
        String normalizedText = normalizeText(text);
        if (normalizedName == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "自定义音色名称不能为空");
        }
        if (normalizedText == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参考音频文本不能为空");
        }

        try {
            HttpHeaders headers = siliconFlowHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", normalizedModel);
            body.add("customName", normalizedName);
            body.add("text", normalizedText);
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return sanitizeFileName(file.getOriginalFilename());
                }
            });

            ResponseEntity<String> response = restTemplate.exchange(
                    siliconFlowUrl("/v1/uploads/audio/voice"),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String uri = root.has("uri") && !root.get("uri").isNull() ? root.get("uri").asText() : null;
            if (uri == null || uri.isBlank()) {
                throw new BusinessException(ErrorCode.NETWORK_ERROR, "SiliconFlow 未返回音色 URI");
            }
            ttsConfigService.updateSiliconFlowVoiceUri(uri);
            return SiliconFlowVoiceDTO.builder()
                    .model(normalizedModel)
                    .customName(normalizedName)
                    .text(normalizedText)
                    .uri(uri)
                    .build();
        } catch (HttpStatusCodeException e) {
            throw new BusinessException(ErrorCode.NETWORK_ERROR, siliconFlowErrorMessage(e), e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("上传 SiliconFlow 参考音频失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.NETWORK_ERROR, "上传 SiliconFlow 参考音频失败", e);
        }
    }

    public List<SiliconFlowVoiceDTO> listSiliconFlowVoices() {
        if (!hasTtsApiKey()) {
            return List.of();
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    siliconFlowUrl("/v1/audio/voice/list"),
                    HttpMethod.GET,
                    new HttpEntity<>(siliconFlowHeaders()),
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode results = root.has("results") ? root.get("results") : root.get("result");
            if (results == null || !results.isArray()) {
                return List.of();
            }
            List<SiliconFlowVoiceDTO> voices = new ArrayList<>();
            for (JsonNode item : results) {
                voices.add(SiliconFlowVoiceDTO.builder()
                        .model(textValue(item, "model"))
                        .customName(textValue(item, "customName"))
                        .text(textValue(item, "text"))
                        .uri(textValue(item, "uri"))
                        .build());
            }
            return voices;
        } catch (Exception e) {
            log.warn("获取 SiliconFlow 音色列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== 状态探测 ====================

    public TtsStatusDTO getStatus() {
        TtsStatusDTO hit = statusCache.get();
        if (hit != null && (System.currentTimeMillis() - hit.getCheckedAt()) <= STATUS_CACHE_TTL_MS) {
            return hit;
        }

        TtsConfigDTO cfg = ttsConfigService.getConfig();
        TtsStatusDTO status = new TtsStatusDTO();
        status.setEnabled(cfg.getEnabled() != null && cfg.getEnabled());
        status.setBaseUrl(cfg.getBaseUrl());
        status.setVoiceModel(cfg.getVoiceModel());
        status.setProvider(cfg.getProvider());
        status.setSiliconFlowModel(cfg.getSiliconFlowModel());
        status.setSiliconFlowVoiceUri(cfg.getSiliconFlowVoiceUri());
        status.setResponseFormat(cfg.getResponseFormat());
        status.setSampleRate(cfg.getSampleRate());
        status.setSpeed(cfg.getSpeed());
        status.setSiliconFlowApiKeyConfigured(hasTtsApiKey());
        status.setSiliconFlowApiKeySource(resolveTtsApiKeySource());
        status.setCheckedAt(System.currentTimeMillis());

        if (!status.isEnabled()) {
            status.setOnline(false);
            status.setMessage("语音功能已关闭");
            statusCache.set(status);
            return status;
        }

        if (TtsConfigService.PROVIDER_SILICONFLOW.equals(status.getProvider())) {
            if (!hasTtsApiKey()) {
                status.setOnline(false);
                status.setMessage("未配置 SiliconFlow API Key");
                statusCache.set(status);
                return status;
            }
            if (status.getSiliconFlowVoiceUri() == null || status.getSiliconFlowVoiceUri().isBlank()) {
                status.setOnline(false);
                status.setMessage("未配置 SiliconFlow 自定义音色 URI");
                statusCache.set(status);
                return status;
            }
            status.setOnline(true);
            status.setMessage("SiliconFlow 已配置");
            statusCache.set(status);
            return status;
        }

        if (status.getBaseUrl() == null || status.getBaseUrl().isBlank()) {
            status.setOnline(false);
            status.setMessage("未配置语音推理服务地址");
            statusCache.set(status);
            return status;
        }

        try {
            URI uri = URI.create(status.getBaseUrl() + "/infer_single");
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(1200))
                    .GET()
                    .build();
            HttpResponse<Void> resp = statusHttpClient.send(req, HttpResponse.BodyHandlers.discarding());
            status.setOnline(true);
            status.setMessage("在线（HTTP " + resp.statusCode() + "）");
        } catch (Exception e) {
            status.setOnline(false);
            status.setMessage("离线：" + e.getClass().getSimpleName());
        }

        statusCache.set(status);
        return status;
    }

    public void clearStatusCache() {
        statusCache.set(null);
    }

    // ==================== 缓存管理 ====================

    public Path cacheDir() {
        return Path.of(fileUploadConfig.getBasePath(), "tts-cache").toAbsolutePath().normalize();
    }

    void cleanupIfDue() {
        long now = System.currentTimeMillis();
        long previous = lastCleanupAt.get();
        long interval = Math.max(1000L, cleanupIntervalMs);
        if ((now - previous) < interval) {
            return;
        }
        if (lastCleanupAt.compareAndSet(previous, now)) {
            cleanup();
        }
    }

    @Scheduled(fixedDelayString = "${tts.cache.cleanup-interval-ms:${TTS_CACHE_CLEANUP_INTERVAL_MS:3600000}}")
    public void cleanup() {
        Path dir = cacheDir();
        if (!Files.isDirectory(dir)) {
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofHours(Math.max(1L, maxAgeHours)));
        List<AudioCacheFile> activeFiles = new ArrayList<>();
        AtomicLong totalBytes = new AtomicLong(0L);
        AtomicLong deletedCount = new AtomicLong(0L);

        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(this::isManagedCacheFile)
                    .forEach(path -> collectOrDeleteExpired(path, cutoff, activeFiles, totalBytes, deletedCount));
        } catch (IOException e) {
            log.warn("扫描 TTS 缓存目录失败: {}", e.getMessage());
            return;
        }

        long capacity = Math.max(0L, maxBytes);
        if (capacity > 0 && totalBytes.get() > capacity) {
            activeFiles.sort(Comparator.comparing(AudioCacheFile::modifiedAt));
            for (AudioCacheFile file : activeFiles) {
                if (totalBytes.get() <= capacity) {
                    break;
                }
                if (deleteCacheFile(file.path())) {
                    totalBytes.addAndGet(-file.size());
                    deletedCount.incrementAndGet();
                }
            }
        }

        long deleted = deletedCount.get();
        if (deleted > 0) {
            log.info("TTS 缓存清理完成: deleted={}, remainingBytes={}", deleted, totalBytes.get());
        }
    }

    // ==================== SiliconFlow Key 解析 ====================

    public String resolveTtsApiKey() {
        return firstNonBlank(
                property("siliconflow.tts-api-key"),
                property("SILICONFLOW_TTS_API_KEY"),
                System.getenv(SOURCE_TTS),
                dotenv(SOURCE_TTS),
                property("siliconflow.api-key"),
                property("SILICONFLOW_API_KEY"),
                System.getenv(SOURCE_COMPAT),
                dotenv(SOURCE_COMPAT),
                property("spring.ai.openai.api-key"),
                property("SPRING_AI_OPENAI_API_KEY"),
                System.getenv(SOURCE_AI),
                dotenv(SOURCE_AI)
        );
    }

    public boolean hasTtsApiKey() {
        return resolveTtsApiKey() != null;
    }

    public String resolveTtsApiKeySource() {
        if (hasAny("siliconflow.tts-api-key", "SILICONFLOW_TTS_API_KEY", SOURCE_TTS)) {
            return SOURCE_TTS;
        }
        if (hasAny("siliconflow.api-key", "SILICONFLOW_API_KEY", SOURCE_COMPAT)) {
            return SOURCE_COMPAT;
        }
        if (hasAny("spring.ai.openai.api-key", "SPRING_AI_OPENAI_API_KEY", SOURCE_AI)) {
            return SOURCE_AI + " fallback";
        }
        return null;
    }

    // ==================== 内部方法：合成 ====================

    private TtsSpeechResponseDTO synthesizeWithGptSovits(TtsConfigDTO config, String text) {
        String baseUrl = normalizeBaseUrl(config.getBaseUrl());
        if (baseUrl == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未配置 GPT-SoVITS 服务地址");
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("dl_url", baseUrl);
            body.put("version", "v4");
            body.put("model_name", defaultText(config.getVoiceModel(), "原神-中文-纳西妲_ZH"));
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

            String requestBody = objectMapper.writeValueAsString(body);
            log.debug("GPT-SoVITS 请求: POST {} body={}", baseUrl + "/infer_single", requestBody);

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/infer_single"))
                    .timeout(Duration.ofSeconds(120))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("GPT-SoVITS 响应异常: HTTP {} body={}", response.statusCode(), response.body());
                throw new BusinessException(ErrorCode.NETWORK_ERROR, "GPT-SoVITS 推理失败（HTTP " + response.statusCode() + "）");
            }

            JsonNode root = objectMapper.readTree(response.body());
            String audioUrl = root.has("audio_url") && !root.get("audio_url").isNull() ? root.get("audio_url").asText() : null;
            if (audioUrl == null || audioUrl.isBlank()) {
                throw new BusinessException(ErrorCode.NETWORK_ERROR, "GPT-SoVITS 未返回音频地址");
            }
            byte[] audio = downloadAudio(normalizeAudioUrl(audioUrl, baseUrl), baseUrl);
            String cachedUrl = saveAudio(audio, "wav");
            return TtsSpeechResponseDTO.builder()
                    .audioUrl(cachedUrl)
                    .provider(TtsConfigService.PROVIDER_GPT_SOVITS)
                    .format("wav")
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("GPT-SoVITS 推理失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.NETWORK_ERROR, "GPT-SoVITS 推理失败", e);
        }
    }

    private TtsSpeechResponseDTO synthesizeWithSiliconFlow(TtsConfigDTO config, String text) {
        if (!hasTtsApiKey()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未配置 SiliconFlow API Key");
        }
        String voiceUri = normalizeText(config.getSiliconFlowVoiceUri());
        if (voiceUri == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未配置 SiliconFlow 自定义音色 URI");
        }
        String format = normalizeFormat(config.getResponseFormat());
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", defaultText(config.getSiliconFlowModel(), TtsConfigService.DEFAULT_SILICON_FLOW_MODEL));
            body.put("input", text);
            body.put("voice", voiceUri);
            body.put("response_format", format);
            body.put("stream", false);
            body.put("sample_rate", normalizeSampleRateForFormat(config.getSampleRate(), format));
            body.put("speed", config.getSpeed() == null ? TtsConfigService.DEFAULT_SPEED : config.getSpeed());
            body.put("gain", 0.0);

            String requestBody = objectMapper.writeValueAsString(body);
            log.debug("SiliconFlow TTS 请求: model={}, format={}, inputLength={}",
                    body.get("model"), format, text.length());

            HttpRequest request = HttpRequest.newBuilder(URI.create(siliconFlowUrl("/v1/audio/speech")))
                    .timeout(Duration.ofSeconds(120))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTtsApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.NETWORK_ERROR, "SiliconFlow 语音生成失败（HTTP " + response.statusCode() + "）");
            }

            String cachedUrl = saveAudio(response.body(), format);
            return TtsSpeechResponseDTO.builder()
                    .audioUrl(cachedUrl)
                    .provider(TtsConfigService.PROVIDER_SILICONFLOW)
                    .format(format)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SiliconFlow 语音生成失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.NETWORK_ERROR, "SiliconFlow 语音生成失败", e);
        }
    }

    private byte[] downloadAudio(String audioUrl, String expectedBaseUrl) throws IOException, InterruptedException {
        String base = normalizeBaseUrl(expectedBaseUrl);
        if (base == null) {
            throw new BusinessException(ErrorCode.NETWORK_ERROR, "缺少允许的音频下载源");
        }
        return downloadAudioFollowingSafeRedirects(URI.create(audioUrl), URI.create(base), 0);
    }

    private byte[] downloadAudioFollowingSafeRedirects(URI uri, URI expectedBaseUri, int redirects)
            throws IOException, InterruptedException {
        if (!isAllowedAudioUri(uri, expectedBaseUri)) {
            throw new BusinessException(ErrorCode.NETWORK_ERROR, "不允许的音频下载地址: " + safeHost(uri));
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (isRedirectStatus(response.statusCode())) {
            if (redirects >= 3) {
                throw new BusinessException(ErrorCode.NETWORK_ERROR, "TTS 音频下载重定向次数过多");
            }
            String location = response.headers().firstValue(HttpHeaders.LOCATION).orElse(null);
            if (location == null || location.isBlank()) {
                throw new BusinessException(ErrorCode.NETWORK_ERROR, "TTS 音频下载重定向缺少 Location");
            }
            URI next = uri.resolve(location.trim());
            return downloadAudioFollowingSafeRedirects(next, expectedBaseUri, redirects + 1);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ErrorCode.NETWORK_ERROR, "下载 TTS 音频失败（HTTP " + response.statusCode() + "）");
        }
        return response.body();
    }

    private String saveAudio(byte[] audio, String format) throws IOException {
        if (audio == null || audio.length == 0) {
            throw new BusinessException(ErrorCode.NETWORK_ERROR, "TTS 返回空音频");
        }
        String ext = normalizeFormat(format);
        String fileName = UUID.randomUUID() + "." + ext;
        Files.createDirectories(cacheDir());
        Files.write(cacheDir().resolve(fileName), audio);
        cleanupIfDue();
        return "/tts/audio/" + fileName;
    }

    // ==================== 内部方法：缓存清理 ====================

    private void collectOrDeleteExpired(
            Path path, Instant cutoff,
            List<AudioCacheFile> activeFiles, AtomicLong totalBytes, AtomicLong deletedCount) {
        try {
            FileTime modifiedAt = Files.getLastModifiedTime(path);
            long size = Files.size(path);
            if (modifiedAt.toInstant().isBefore(cutoff)) {
                if (deleteCacheFile(path)) {
                    deletedCount.incrementAndGet();
                }
                return;
            }
            activeFiles.add(new AudioCacheFile(path, size, modifiedAt));
            totalBytes.addAndGet(size);
        } catch (IOException e) {
            log.debug("跳过不可读 TTS 缓存文件 {}: {}", path.getFileName(), e.getMessage());
        }
    }

    private boolean isManagedCacheFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        return fileName.matches("[a-f0-9\\-]{36}\\.(mp3|wav|opus|pcm)");
    }

    private boolean deleteCacheFile(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("删除 TTS 缓存文件失败 {}: {}", path.getFileName(), e.getMessage());
            return false;
        }
    }

    private record AudioCacheFile(Path path, long size, FileTime modifiedAt) {
    }

    // ==================== 内部方法：SiliconFlow Key ====================

    private boolean hasAny(String propertyName, String envPropertyName, String dotenvName) {
        return firstNonBlank(
                property(propertyName),
                property(envPropertyName),
                System.getenv(dotenvName),
                dotenv(dotenvName)
        ) != null;
    }

    private String property(String name) {
        return normalize(environment.getProperty(name));
    }

    private String dotenv(String name) {
        return dotenv().get(name);
    }

    private Map<String, String> dotenv() {
        Map<String, String> hit = dotenvCache;
        if (hit != null) {
            return hit;
        }
        Map<String, String> loaded = new HashMap<>();
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 6 && current != null; i++) {
            Path envFile = current.resolve(".env");
            if (Files.isRegularFile(envFile)) {
                loaded.putAll(readDotenv(envFile));
                break;
            }
            current = current.getParent();
        }
        dotenvCache = loaded;
        return loaded;
    }

    private Map<String, String> readDotenv(Path envFile) {
        Map<String, String> values = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(envFile);
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, idx).trim();
                String normalized = normalize(unquote(trimmed.substring(idx + 1).trim()));
                if (normalized != null) {
                    values.put(key, normalized);
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read .env file", e);
        }
        return values;
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    // ==================== 内部方法：通用 ====================

    private HttpHeaders siliconFlowHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(resolveTtsApiKey());
        return headers;
    }

    private String siliconFlowUrl(String path) {
        String base = normalizeBaseUrl(siliconFlowBaseUrl);
        if (base == null) {
            base = "https://api.siliconflow.cn";
        }
        return base + path;
    }

    private String siliconFlowErrorMessage(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "SiliconFlow 请求失败（HTTP " + e.getStatusCode().value() + "）";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("message")) {
                return "SiliconFlow 请求失败：" + root.get("message").asText();
            }
            if (root.has("error")) {
                return "SiliconFlow 请求失败：" + root.get("error").asText();
            }
        } catch (Exception parseEx) {
            log.debug("Failed to parse error response", parseEx);
        }
        String truncated = body.length() > 200 ? body.substring(0, 200) + "..." : body;
        return "SiliconFlow 请求失败：" + truncated;
    }

    private boolean isAllowedAudioUri(URI uri, URI expectedBaseUri) {
        if (uri == null || expectedBaseUri == null) return false;
        if (!isHttpScheme(uri.getScheme()) || !isHttpScheme(expectedBaseUri.getScheme())) return false;
        if (uri.getHost() == null || expectedBaseUri.getHost() == null) return false;
        return uri.getScheme().equalsIgnoreCase(expectedBaseUri.getScheme())
                && uri.getHost().equalsIgnoreCase(expectedBaseUri.getHost())
                && effectivePort(uri) == effectivePort(expectedBaseUri);
    }

    private boolean isHttpScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private String safeHost(URI uri) {
        return uri == null || uri.getHost() == null ? "unknown" : uri.getHost();
    }

    private String normalizeAudioUrl(String audioUrl, String baseUrl) {
        if (audioUrl == null) return null;
        String u = audioUrl.trim();
        if (u.isEmpty()) return null;
        String base = normalizeBaseUrl(baseUrl);
        if (base == null) return u;
        try {
            if (u.startsWith("/")) return base + u;
            if (!u.startsWith("http://") && !u.startsWith("https://")) return base + "/" + u;
            URI raw = URI.create(u);
            String host = raw.getHost();
            String path = raw.getPath();
            String query = raw.getQuery();
            boolean isLocalHost = host != null && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host));
            if (isLocalHost && path != null && !path.isBlank()) {
                return base + path + (query != null && !query.isBlank() ? ("?" + query) : "");
            }
        } catch (Exception e) {
            log.debug("Failed to normalize audio URL", e);
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

    private String normalizeText(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        return s.isEmpty() ? null : s;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultText(String raw, String defaultValue) {
        String normalized = normalizeText(raw);
        return normalized == null ? defaultValue : normalized;
    }

    private String normalizeFormat(String raw) {
        String normalized = normalizeText(raw);
        if (normalized == null) return TtsConfigService.DEFAULT_RESPONSE_FORMAT;
        String lower = normalized.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "wav", "opus", "pcm" -> lower;
            default -> "mp3";
        };
    }

    private int normalizeSampleRateForFormat(Integer sampleRate, String format) {
        int value = sampleRate == null ? TtsConfigService.DEFAULT_SAMPLE_RATE : sampleRate;
        String normalizedFormat = normalizeFormat(format);
        if ("opus".equals(normalizedFormat)) {
            return 48000;
        }
        if ("mp3".equals(normalizedFormat)) {
            return value == 32000 ? 32000 : 44100;
        }
        if (value == 8000 || value == 16000 || value == 24000 || value == 32000 || value == 44100) {
            return value;
        }
        return 44100;
    }

    private String sanitizeFileName(String name) {
        String safe = name == null || name.isBlank() ? "reference.wav" : name.trim();
        return safe.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String textValue(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
