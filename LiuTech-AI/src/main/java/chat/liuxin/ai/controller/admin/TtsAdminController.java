package chat.liuxin.ai.controller.admin;

import chat.liuxin.ai.dto.tts.SiliconFlowVoiceDTO;
import chat.liuxin.ai.dto.tts.TtsConfigDTO;
import chat.liuxin.ai.dto.tts.TtsConfigRequest;
import chat.liuxin.ai.dto.tts.TtsSpeechDTO;
import chat.liuxin.ai.dto.tts.TtsSpeechRequest;
import chat.liuxin.ai.dto.tts.TtsStatusDTO;
import chat.liuxin.ai.service.tts.TtsConfigService;
import chat.liuxin.ai.service.tts.TtsSpeechService;
import chat.liuxin.ai.service.tts.TtsStatusService;
import chat.liuxin.ai.service.tts.TtsVoiceCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** AI 服务拥有的 TTS 管理接口。 */
@Slf4j
@RestController
@RequestMapping("/ai/admin/tts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class TtsAdminController {

    private final TtsConfigService ttsConfigService;
    private final TtsStatusService ttsStatusService;
    private final TtsVoiceCatalogService ttsVoiceCatalogService;
    private final TtsSpeechService ttsSpeechService;

    @GetMapping("/config")
    public TtsConfigDTO getConfig() {
        return ttsConfigService.getConfig();
    }

    @PutMapping("/config")
    public TtsConfigDTO updateConfig(@RequestBody TtsConfigRequest config) {
        TtsConfigDTO updated = ttsConfigService.updateConfig(config);
        ttsStatusService.clearCache();
        log.info("AI TTS 配置已更新: provider={}, enabled={}", updated.getProvider(), updated.getEnabled());
        return updated;
    }

    @GetMapping("/status")
    public TtsStatusDTO status() {
        return ttsStatusService.getStatus();
    }

    @GetMapping("/voices")
    public List<String> voices(@RequestParam(required = false) String baseUrl) {
        TtsConfigDTO config = ttsConfigService.getConfig();
        String effectiveBaseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : config.getBaseUrl();
        return ttsVoiceCatalogService.listVoiceModels(effectiveBaseUrl);
    }

    @GetMapping("/siliconflow/voices")
    public List<SiliconFlowVoiceDTO> siliconFlowVoices() {
        return ttsSpeechService.listSiliconFlowVoices();
    }

    @PostMapping("/siliconflow/voice")
    public SiliconFlowVoiceDTO uploadSiliconFlowVoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam("model") String model,
            @RequestParam("customName") String customName,
            @RequestParam("text") String text) {
        SiliconFlowVoiceDTO voice = ttsSpeechService.uploadSiliconFlowVoice(file, model, customName, text);
        ttsStatusService.clearCache();
        log.info("AI TTS SiliconFlow 音色已上传: model={}, customName={}", model, customName);
        return voice;
    }

    @PostMapping("/test-speech")
    public TtsSpeechDTO testSpeech(@Valid @RequestBody TtsSpeechRequest request) {
        return ttsSpeechService.synthesize(request.getText());
    }
}
