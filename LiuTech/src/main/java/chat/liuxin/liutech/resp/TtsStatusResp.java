package chat.liuxin.liutech.resp;

import lombok.Data;

/**
 * TTS（语音推理）在线状态（管理端响应）
 */
@Data
public class TtsStatusResp {

    /**
     * 管理端全局开关
     */
    private boolean enabled;

    /**
     * 当前服务是否可用（可连通）
     */
    private boolean online;

    /**
     * 当前生效的 TTS 基础地址（用于前端调试展示）
     */
    private String baseUrl;

    /**
     * 当前生效的语音模型
     */
    private String voiceModel;

    /**
     * 当前 TTS 引擎。
     */
    private String provider;

    /**
     * SiliconFlow 当前模型。
     */
    private String siliconFlowModel;

    /**
     * SiliconFlow 当前音色 URI。
     */
    private String siliconFlowVoiceUri;

    /**
     * 输出格式。
     */
    private String responseFormat;

    /**
     * 输出采样率。
     */
    private Integer sampleRate;

    /**
     * 语速。
     */
    private Double speed;

    /**
     * SiliconFlow API Key 是否已经在环境中配置。
     */
    private boolean siliconFlowApiKeyConfigured;

    /**
     * SiliconFlow API Key 来源，只暴露变量名，不暴露密钥值。
     */
    private String siliconFlowApiKeySource;

    /**
     * 最近一次探测时间（毫秒时间戳）
     */
    private long checkedAt;

    /**
     * 额外提示信息（例如：未配置地址/探测异常原因）
     */
    private String message;
}
