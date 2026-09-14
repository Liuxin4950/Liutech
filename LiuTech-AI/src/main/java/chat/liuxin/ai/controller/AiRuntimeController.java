package chat.liuxin.ai.controller;

import chat.liuxin.ai.dto.AiRuntimeDTO;
import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.dto.TtsPublicStatusDTO;
import chat.liuxin.ai.service.AiModelConfigService;
import chat.liuxin.ai.service.tts.TtsSpeechService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 服务自己的模型与 TTS 运行时快照。 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiRuntimeController {

    private static final String FALLBACK_DEFAULT_MODEL = "deepseek-ai/DeepSeek-V3.2";

    private final AiModelConfigService modelConfigService;
    private final TtsSpeechService ttsSpeechService;

    @GetMapping("/runtime")
    public AiRuntimeDTO runtime() {
        AiRuntimeDTO dto = new AiRuntimeDTO();
        dto.setAiOnline(true);
        dto.setAiMessage("在线");
        dto.setDefaultModel(modelConfigService.getDefaultModel()
                .map(ModelConfigDTO::getModelName)
                .orElse(FALLBACK_DEFAULT_MODEL));
        dto.setTts(TtsPublicStatusDTO.from(ttsSpeechService.getStatus()));
        return dto;
    }
}
