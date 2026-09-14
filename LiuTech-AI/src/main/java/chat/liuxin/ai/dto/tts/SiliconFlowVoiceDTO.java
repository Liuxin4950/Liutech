package chat.liuxin.ai.dto.tts;

import lombok.Builder;
import lombok.Data;

/**
 * SiliconFlow 自定义音色信息。
 */
@Data
@Builder
public class SiliconFlowVoiceDTO {
    private String model;
    private String customName;
    private String text;
    private String uri;
}
