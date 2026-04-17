package chat.liuxin.liutech.model.dto;

import lombok.Data;

/**
 * TTS（语音推理）配置 DTO
 */
@Data
public class TtsConfigDTO {

    /**
     * 是否启用语音功能（全局开关）
     */
    private Boolean enabled;

    /**
     * TTS 服务基础地址，例如：http://127.0.0.1:8000
     */
    private String baseUrl;

    /**
     * 选中的语音模型名称
     * 例如：原神-中文-纳西妲_ZH
     */
    private String voiceModel;
}
