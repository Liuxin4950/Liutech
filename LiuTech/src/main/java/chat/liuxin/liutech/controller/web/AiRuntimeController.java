package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.dto.AiRuntimeDTO;
import chat.liuxin.liutech.service.AiRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 运行时公共接口
 *
 * 说明：
 * - 提供默认模型与 TTS 状态的统一快照
 * - 前台只需要请求这一处，不再分别判断多个来源
 */
@RestController
@RequestMapping("/runtime")
public class AiRuntimeController {

    private final AiRuntimeService aiRuntimeService;

    public AiRuntimeController(AiRuntimeService aiRuntimeService) {
        this.aiRuntimeService = aiRuntimeService;
    }

    @GetMapping("/ai")
    public Result<AiRuntimeDTO> runtime() {
        return Result.success(aiRuntimeService.getRuntime());
    }
}
