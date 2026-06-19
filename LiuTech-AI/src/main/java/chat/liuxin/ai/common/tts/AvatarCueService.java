package chat.liuxin.ai.common.tts;

import chat.liuxin.ai.dto.AvatarCuePayload;
import chat.liuxin.ai.infra.config.AvatarCueProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 基于文本语义的首版 Live2D cue 生成器。
 *
 * <p>情绪关键词从 {@link AvatarCueProperties} 配置加载，支持运行时调整。
 */
@Component
@RequiredArgsConstructor
public class AvatarCueService {

    private final AvatarCueProperties properties;

    public AvatarCuePayload neutral(int seq, Long conversationId) {
        return AvatarCuePayload.builder()
                .seq(seq)
                .conversationId(conversationId)
                .expression("neutral")
                .motion(null)
                .intensity(0.0)
                .durationMs(0)
                .text("")
                .build();
    }

    public AvatarCuePayload fromText(int seq, Long conversationId, String text) {
        String source = text == null ? "" : text.trim();
        String normalized = source.toLowerCase();
        String expression = inferExpression(normalized);
        double intensity = inferIntensity(normalized, source.length());
        int durationMs = Math.max(
                properties.getMinDurationMs(),
                Math.min(properties.getMaxDurationMs(),
                        properties.getDurationBaseMs() + source.length() * properties.getDurationPerCharMs()));

        return AvatarCuePayload.builder()
                .seq(seq)
                .conversationId(conversationId)
                .expression(expression)
                .motion(null)
                .intensity(intensity)
                .durationMs(durationMs)
                .text(source)
                .build();
    }

    private String inferExpression(String text) {
        Map<String, List<String>> keywords = properties.getEmotionKeywords();
        for (String emotion : properties.getEmotionPriority()) {
            List<String> needles = keywords.get(emotion);
            if (needles != null && containsAny(text, needles)) {
                return emotion;
            }
        }
        return "neutral";
    }

    private double inferIntensity(String text, int length) {
        double base = properties.getBaseIntensity();
        if (containsAny(text, properties.getIntensityBoosters())) {
            base += properties.getIntensityBoost();
        }
        if (length > properties.getLongTextThreshold()) {
            base -= properties.getLongTextPenalty();
        }
        return Math.max(properties.getMinIntensity(), Math.min(properties.getMaxIntensity(), base));
    }

    private boolean containsAny(String text, List<String> needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
