package chat.liuxin.liutech.controller.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.dto.TtsConfigDTO;
import chat.liuxin.liutech.model.dto.TtsStatusDTO;
import chat.liuxin.liutech.service.TtsConfigService;
import chat.liuxin.liutech.service.TtsStatusService;

/**
 * TTS（语音推理）公共接口
 *
 * 说明：
 * - 前端用它判断“语音功能是否可用”
 * - 不需要登录即可访问（由 SecurityConfig 放行）
 */
@RestController
@RequestMapping("/tts")
public class TtsController {

    @Autowired
    private TtsStatusService ttsStatusService;

    @Autowired
    private TtsConfigService ttsConfigService;

    @GetMapping("/status")
    public Result<TtsStatusDTO> status() {
        return Result.success(ttsStatusService.getStatus());
    }

    @GetMapping("/config")
    public Result<TtsConfigDTO> config() {
        return Result.success(ttsConfigService.getConfig());
    }
}
