package chat.liuxin.ai.dto;

import lombok.Data;

@Data
public class AiRuntimeDTO {
    private boolean aiOnline;
    private String aiMessage;
    private String defaultModel;
    private TtsPublicStatusDTO tts;
}
