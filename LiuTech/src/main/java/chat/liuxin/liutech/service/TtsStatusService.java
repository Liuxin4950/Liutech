package chat.liuxin.liutech.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import chat.liuxin.liutech.model.dto.TtsConfigDTO;
import chat.liuxin.liutech.model.dto.TtsStatusDTO;

/**
 * TTS 服务在线探测
 *
 * 设计目标：
 * - 统一由后端判断 TTS 是否在线，前端只要问“能不能用”
 * - 探测结果做短期缓存，避免前端轮询导致频繁打 TTS 服务
 *
 * 探测策略（尽量轻量且兼容）：
 * - 访问 baseUrl + "/infer_single" 发起 GET
 * - 即便返回 405（方法不允许），也说明服务在线
 * - 只要能拿到 HTTP 响应就算在线；连接失败/超时算离线
 */
@Service
public class TtsStatusService {

    private static final long CACHE_TTL_MS = 5000L;

    private final AtomicReference<TtsStatusDTO> cached = new AtomicReference<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(800))
            .build();

    @Autowired
    private TtsConfigService ttsConfigService;

    @Autowired
    private SiliconFlowKeyResolver siliconFlowKeyResolver;

    public TtsStatusDTO getStatus() {
        TtsStatusDTO hit = cached.get();
        if (hit != null && (System.currentTimeMillis() - hit.getCheckedAt()) <= CACHE_TTL_MS) {
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
        status.setSiliconFlowApiKeyConfigured(hasSiliconFlowApiKey());
        status.setSiliconFlowApiKeySource(siliconFlowKeyResolver.resolveTtsApiKeySource());
        status.setCheckedAt(System.currentTimeMillis());

        if (!status.isEnabled()) {
            status.setOnline(false);
            status.setMessage("语音功能已关闭");
            cached.set(status);
            return status;
        }

        if (TtsConfigService.PROVIDER_SILICONFLOW.equals(status.getProvider())) {
            if (!hasSiliconFlowApiKey()) {
                status.setOnline(false);
                status.setMessage("未配置 SiliconFlow API Key");
                cached.set(status);
                return status;
            }
            if (status.getSiliconFlowVoiceUri() == null || status.getSiliconFlowVoiceUri().isBlank()) {
                status.setOnline(false);
                status.setMessage("未配置 SiliconFlow 自定义音色 URI");
                cached.set(status);
                return status;
            }
            status.setOnline(true);
            status.setMessage("SiliconFlow 已配置");
            cached.set(status);
            return status;
        }

        if (status.getBaseUrl() == null || status.getBaseUrl().isBlank()) {
            status.setOnline(false);
            status.setMessage("未配置语音推理服务地址");
            cached.set(status);
            return status;
        }

        try {
            URI uri = URI.create(status.getBaseUrl() + "/infer_single");
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(1200))
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());

            status.setOnline(true);
            status.setMessage("在线（HTTP " + resp.statusCode() + "）");
        } catch (Exception e) {
            status.setOnline(false);
            status.setMessage("离线：" + e.getClass().getSimpleName());
        }

        cached.set(status);
        return status;
    }

    public void clearCache() {
        cached.set(null);
    }

    private boolean hasSiliconFlowApiKey() {
        return siliconFlowKeyResolver.hasTtsApiKey();
    }
}
