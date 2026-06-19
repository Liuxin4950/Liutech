package chat.liuxin.ai.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TTS 文本切分器配置。
 *
 * <p>将 TtsSegmenter 中硬编码的切分阈值提取为可配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.tts.segmenter")
public class TtsSegmenterProperties {

    /** 首段最小发送长度（字符数） */
    private int firstSegmentMinLen = 20;

    /** 首段硬切长度（无标点时强制切分） */
    private int firstSegmentHardCutLen = 40;

    /** 后续段最小发送长度 */
    private int followSegmentMinLen = 60;

    /** 后续段软标点切分长度 */
    private int followSegmentSoftPunctLen = 80;

    /** 后续段硬切长度 */
    private int followSegmentHardCutLen = 100;
}
