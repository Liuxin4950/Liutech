package chat.liuxin.liutech.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.dto.SiliconFlowVoiceDTO;
import chat.liuxin.liutech.model.dto.TtsConfigDTO;
import chat.liuxin.liutech.model.dto.TtsSpeechRequestDTO;
import chat.liuxin.liutech.model.dto.TtsSpeechResponseDTO;
import chat.liuxin.liutech.model.dto.TtsStatusDTO;
import chat.liuxin.liutech.service.TtsConfigService;
import chat.liuxin.liutech.service.TtsSpeechService;
import jakarta.validation.Valid;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 管理端：语音推理（TTS）配置
 */
@RestController
@RequestMapping("/admin/tts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
 extends BaseAdminController {

    private final TtsConfigService ttsConfigService;

    private final TtsSpeechService ttsSpeechService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1000))
            .build();

    @GetMapping("/config")
    public Result<TtsConfigDTO> getConfig() {
        return Result.success(ttsConfigService.getConfig());
    }

    @PutMapping("/config")
    @OperationLog(action = "update", targetType = "tts", description = "更新语音推理配置")
    public Result<String> updateConfig(@RequestBody TtsConfigDTO config) {
        try {
            ttsConfigService.updateConfig(config);
            ttsSpeechService.clearStatusCache();
            return Result.success("更新成功");
        } catch (Exception e) {
            return handleException(e, "更新语音推理配置");
        }
    }

    @GetMapping("/status")
    public Result<TtsStatusDTO> status() {
        return Result.success(ttsSpeechService.getStatus());
    }

    @GetMapping("/voices")
    public Result<List<String>> voices(@RequestParam(required = false) String baseUrl) {
        TtsConfigDTO config = ttsConfigService.getConfig();
        String effectiveBaseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : config.getBaseUrl();
        return Result.success(listVoiceModels(effectiveBaseUrl));
    }

    @GetMapping("/siliconflow/voices")
    public Result<List<SiliconFlowVoiceDTO>> siliconFlowVoices() {
        return Result.success(ttsSpeechService.listSiliconFlowVoices());
    }

    @PostMapping("/siliconflow/voice")
    @OperationLog(action = "upload", targetType = "tts", description = "上传 SiliconFlow 参考音频")
    public Result<SiliconFlowVoiceDTO> uploadSiliconFlowVoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam("model") String model,
            @RequestParam("customName") String customName,
            @RequestParam("text") String text) {
        SiliconFlowVoiceDTO voice = ttsSpeechService.uploadSiliconFlowVoice(file, model, customName, text);
        ttsSpeechService.clearStatusCache();
        return Result.success(voice);
    }

    @PostMapping("/test-speech")
    @OperationLog(action = "test", targetType = "tts", description = "测试语音合成")
    public Result<TtsSpeechResponseDTO> testSpeech(@Valid @RequestBody TtsSpeechRequestDTO request) {
        return Result.success(ttsSpeechService.synthesize(request.getText()));
    }

    /**
     * 从 GPT-SoVITS 服务获取可用语音模型列表（原 TtsVoiceCatalogService 内联）
     */
    private List<String> listVoiceModels(String baseUrl) {
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
            Iterator<String> fields = models.fieldNames();
            while (fields.hasNext()) {
                result.add(fields.next());
            }
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
