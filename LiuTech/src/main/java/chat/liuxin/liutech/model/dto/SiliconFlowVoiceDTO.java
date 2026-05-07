package chat.liuxin.liutech.model.dto;

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
