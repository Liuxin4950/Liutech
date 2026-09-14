package chat.liuxin.ai.dto.tts;

import lombok.Builder;
import lombok.Data;

/**
 * 统一 TTS 推理响应。
 */
@Data
@Builder
public class TtsSpeechDTO {

    /**
     * 前端可播放的 AI 服务相对地址，通常是 tts/audio/{fileName}。
     */
    private String audioUrl;

    /**
     * 实际使用的 TTS 引擎。
     */
    private String provider;

    /**
     * 音频格式。
     */
    private String format;
}
