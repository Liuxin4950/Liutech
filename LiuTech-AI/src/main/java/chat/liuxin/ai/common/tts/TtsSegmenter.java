package chat.liuxin.ai.common.tts;

import chat.liuxin.ai.infra.config.TtsSegmenterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * TTS 文本切分器。
 *
 * <p>切分阈值从 {@link TtsSegmenterProperties} 配置加载，支持运行时调整。
 */
@Component
@RequiredArgsConstructor
public class TtsSegmenter {

    private final TtsSegmenterProperties properties;

    public List<String> splitAll(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;

        StringBuilder buffer = new StringBuilder(text);
        boolean firstSegmentSent = false;
        while (buffer.length() > 0) {
            List<String> segments = extractSegments(buffer, firstSegmentSent);
            if (segments.isEmpty()) {
                String rest = buffer.toString().trim();
                buffer.setLength(0);
                if (containsSpeakableText(rest)) {
                    result.add(rest);
                }
                break;
            }
            result.addAll(segments);
            firstSegmentSent = true;
        }
        return result;
    }

    public List<String> extractSegments(StringBuilder buffer, boolean firstSegmentSent) {
        List<String> out = new ArrayList<>();
        if (buffer == null || buffer.length() == 0) return out;

        while (true) {
            int len = buffer.length();
            final int minSendLen = firstSegmentSent
                    ? properties.getFollowSegmentMinLen()
                    : properties.getFirstSegmentMinLen();
            final int hardCutLen = firstSegmentSent
                    ? properties.getFollowSegmentHardCutLen()
                    : properties.getFirstSegmentHardCutLen();
            final boolean allowSoftPunctuation = !firstSegmentSent;

            if (len < minSendLen) break;

            int cut = -1;
            int scanLen = Math.min(len, hardCutLen);
            int lastStrongPuncBeforeLimit = -1;
            int lastSoftPuncBeforeLimit = -1;
            for (int i = 0; i < scanLen; i++) {
                char c = buffer.charAt(i);
                if (isStrongTtsCutPunctuation(c)) {
                    lastStrongPuncBeforeLimit = i + 1;
                } else if (allowSoftPunctuation && isSoftTtsCutPunctuation(c)) {
                    lastSoftPuncBeforeLimit = i + 1;
                }
            }

            if (lastStrongPuncBeforeLimit >= minSendLen) {
                cut = lastStrongPuncBeforeLimit;
            } else if (!firstSegmentSent && lastSoftPuncBeforeLimit >= minSendLen) {
                cut = lastSoftPuncBeforeLimit;
            } else if (firstSegmentSent && lastSoftPuncBeforeLimit >= properties.getFollowSegmentSoftPunctLen()) {
                cut = lastSoftPuncBeforeLimit;
            } else if (len >= hardCutLen) {
                cut = hardCutLen;
            }

            if (cut <= 0) break;

            String segment = buffer.substring(0, cut).trim();
            buffer.delete(0, cut);
            if (!segment.isEmpty()) out.add(segment);
            firstSegmentSent = true;
        }

        return out;
    }

    public boolean containsSpeakableText(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) return true;
            Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
            if (b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || b == Character.UnicodeBlock.HIRAGANA
                    || b == Character.UnicodeBlock.KATAKANA
                    || b == Character.UnicodeBlock.HANGUL_SYLLABLES) {
                return true;
            }
        }
        return false;
    }

    private boolean isStrongTtsCutPunctuation(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；' || c == '\n'
                || c == '!' || c == '?' || c == ';';
    }

    private boolean isSoftTtsCutPunctuation(char c) {
        return c == '，' || c == '、' || c == ',' || c == '：' || c == ':';
    }
}
