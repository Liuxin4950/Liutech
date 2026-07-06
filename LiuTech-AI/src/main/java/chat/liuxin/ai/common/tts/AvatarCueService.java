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
 * 情绪关键词从 {@link AvatarCueProperties} 配置加载，支持运行时调整。
 */
@Component
@RequiredArgsConstructor
public class AvatarCueService {

    private final AvatarCueProperties properties;

    /**
     * 构造一个中性 cue,表情复位 (neutral)、强度和时长为 0。
     *
     * 通常在会话开始或一段发言结束时下发,让 Live2D 模型回到默认动作,避免残留上一句的情绪。
     */
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

    /**
     * 根据一段回复文本推断 Live2D 的表情、强度和持续时长,生成 cue 事件发给前端。
     *
     * 表情:小写后依 emotionPriority 顺序匹配 emotionKeywords,命中即用,否则退化为 neutral。
     * 强度:baseIntensity 起步,含 intensityBoosters 关键词则加成,过长文本 (>longTextThreshold) 减成,
     * 最终裁剪到 [minIntensity, maxIntensity]。
     * 时长:durationBaseMs + 字符数 * durationPerCharMs,再裁剪到配置的最小/最大区间。
     */
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

    /**
     * 按 emotionPriority 顺序查找关键词,命中即返回;都未命中返回 "neutral"。
     * 优先级决定了"喜、怒、悲同时出现时以哪个为主"。
     */
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

    /**
     * 计算表情强度:基线 + 强化词加成 - 长文本惩罚,最后夹紧到配置区间。
     * 长文本降强度是为了避免整段说明性回复被判成"高亢",看起来违和。
     */
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
