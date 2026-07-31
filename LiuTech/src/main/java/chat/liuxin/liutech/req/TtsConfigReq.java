package chat.liuxin.liutech.req;

import lombok.Data;

/**
 * 管理端更新 TTS 配置的请求体
 */
@Data
public class TtsConfigReq {

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

    /**
     * TTS 引擎：GPT_SOVITS / SILICONFLOW
     */
    private String provider;

    /**
     * SiliconFlow TTS 模型名称。
     */
    private String siliconFlowModel;

    /**
     * SiliconFlow 上传参考音频后返回的 speech:... 音色 URI。
     */
    private String siliconFlowVoiceUri;

    /**
     * 输出格式：mp3 / wav / opus / pcm。
     */
    private String responseFormat;

    /**
     * 输出采样率。
     */
    private Integer sampleRate;

    /**
     * 语速，SiliconFlow 支持 0.25 - 4.0。
     */
    private Double speed;
}
