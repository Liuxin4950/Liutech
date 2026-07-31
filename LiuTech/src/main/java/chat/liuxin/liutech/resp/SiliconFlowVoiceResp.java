package chat.liuxin.liutech.resp;

import lombok.Builder;
import lombok.Data;

/**
 * SiliconFlow 自定义音色信息。
 */
@Data
@Builder
public class SiliconFlowVoiceResp {
    private String model;
    private String customName;
    private String text;
    private String uri;
}
