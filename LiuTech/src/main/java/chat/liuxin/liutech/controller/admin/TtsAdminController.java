package chat.liuxin.liutech.controller.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.dto.TtsConfigDTO;
import chat.liuxin.liutech.model.dto.TtsStatusDTO;
import chat.liuxin.liutech.service.TtsConfigService;
import chat.liuxin.liutech.service.TtsStatusService;
import chat.liuxin.liutech.service.TtsVoiceCatalogService;

import java.util.List;

/**
 * 管理端：语音推理（TTS）配置
 */
@RestController
@RequestMapping("/admin/tts")
@PreAuthorize("hasRole('ADMIN')")
public class TtsAdminController extends BaseAdminController {

    @Autowired
    private TtsConfigService ttsConfigService;

    @Autowired
    private TtsStatusService ttsStatusService;

    @Autowired
    private TtsVoiceCatalogService ttsVoiceCatalogService;

    @GetMapping("/config")
    public Result<TtsConfigDTO> getConfig() {
        return Result.success(ttsConfigService.getConfig());
    }

    @PutMapping("/config")
    @OperationLog(action = "update", targetType = "tts", description = "更新语音推理配置")
    public Result<String> updateConfig(@RequestBody TtsConfigDTO config) {
        try {
            ttsConfigService.updateConfig(config);
            return Result.success("更新成功");
        } catch (Exception e) {
            return handleException(e, "更新语音推理配置");
        }
    }

    @GetMapping("/status")
    public Result<TtsStatusDTO> status() {
        return Result.success(ttsStatusService.getStatus());
    }

    @GetMapping("/voices")
    public Result<List<String>> voices(@RequestParam(required = false) String baseUrl) {
        TtsConfigDTO config = ttsConfigService.getConfig();
        String effectiveBaseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : config.getBaseUrl();
        return Result.success(ttsVoiceCatalogService.listVoiceModels(effectiveBaseUrl));
    }
}
