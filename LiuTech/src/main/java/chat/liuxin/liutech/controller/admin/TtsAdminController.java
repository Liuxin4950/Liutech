package chat.liuxin.liutech.controller.admin;

import lombok.RequiredArgsConstructor;
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
import chat.liuxin.liutech.req.TtsConfigReq;
import chat.liuxin.liutech.req.TtsSpeechReq;
import chat.liuxin.liutech.resp.SiliconFlowVoiceResp;
import chat.liuxin.liutech.resp.TtsConfigResp;
import chat.liuxin.liutech.resp.TtsSpeechResp;
import chat.liuxin.liutech.resp.TtsStatusResp;
import chat.liuxin.liutech.service.TtsConfigService;
import chat.liuxin.liutech.service.TtsSpeechService;
import chat.liuxin.liutech.service.TtsStatusService;
import chat.liuxin.liutech.service.TtsVoiceCatalogService;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 管理端：语音推理（TTS）配置
 */
@RestController
@RequestMapping("/admin/tts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class TtsAdminController extends BaseAdminController {

    private final TtsConfigService ttsConfigService;

    private final TtsStatusService ttsStatusService;

    private final TtsVoiceCatalogService ttsVoiceCatalogService;

    private final TtsSpeechService ttsSpeechService;

    @GetMapping("/config")
    public Result<TtsConfigResp> getConfig() {
        return Result.success(ttsConfigService.getConfig());
    }

    @PutMapping("/config")
    @OperationLog(action = "update", targetType = "tts", description = "更新语音推理配置")
    public Result<String> updateConfig(@RequestBody TtsConfigReq config) {
        ttsConfigService.updateConfig(config);
        ttsStatusService.clearCache();
        return Result.success("更新成功");
    }

    @GetMapping("/status")
    public Result<TtsStatusResp> status() {
        return Result.success(ttsStatusService.getStatus());
    }

    @GetMapping("/voices")
    public Result<List<String>> voices(@RequestParam(required = false) String baseUrl) {
        TtsConfigResp config = ttsConfigService.getConfig();
        String effectiveBaseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : config.getBaseUrl();
        return Result.success(ttsVoiceCatalogService.listVoiceModels(effectiveBaseUrl));
    }

    @GetMapping("/siliconflow/voices")
    public Result<List<SiliconFlowVoiceResp>> siliconFlowVoices() {
        return Result.success(ttsSpeechService.listSiliconFlowVoices());
    }

    @PostMapping("/siliconflow/voice")
    @OperationLog(action = "upload", targetType = "tts", description = "上传 SiliconFlow 参考音频")
    public Result<SiliconFlowVoiceResp> uploadSiliconFlowVoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam("model") String model,
            @RequestParam("customName") String customName,
            @RequestParam("text") String text) {
        SiliconFlowVoiceResp voice = ttsSpeechService.uploadSiliconFlowVoice(file, model, customName, text);
        ttsStatusService.clearCache();
        return Result.success(voice);
    }

    @PostMapping("/test-speech")
    @OperationLog(action = "test", targetType = "tts", description = "测试语音合成")
    public Result<TtsSpeechResp> testSpeech(@Valid @RequestBody TtsSpeechReq request) {
        return Result.success(ttsSpeechService.synthesize(request.getText()));
    }
}
