package chat.liuxin.ai.common.tts;

import chat.liuxin.ai.infra.config.TtsSegmenterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * TTS 文本切分器。
 *
 * 切分阈值从 {@link TtsSegmenterProperties} 配置加载，支持运行时调整。
 */
@Component
@RequiredArgsConstructor
public class TtsSegmenter {

    private final TtsSegmenterProperties properties;

    /**
     * 将完整文本一次性切分为可播报的语音片段列表,用于非流式场景(如整段回复重播)。
     *
     * 内部循环调用 {@link #extractSegments},直到 buffer 中剩余长度低于阈值,
     * 剩余尾巴若包含可播报字符则作为最后一段返回。
     */
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

    /**
     * 流式增量切分:从 buffer 头部尽可能多地取出可播报片段并返回,已切走的字符从 buffer 移除。
     *
     * 首段(firstSegmentSent=false)使用更小的 firstSegmentMinLen/HardCutLen,
     * 目的是尽快开播降低首帧延迟,并允许软标点(逗号、顿号、冒号)作为切分点。
     * 后续段(firstSegmentSent=true)优先匹配强标点(。！？；\n)满足 followSegmentMinLen,
     * 强标点缺失时若软标点位置超过 followSegmentSoftPunctLen 也可切,再不行才走 HardCutLen 硬切。
     * 达不到 minSendLen 或没有可用切点时直接返回,让调用方继续追加流数据。
     */
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

    /**
     * 判断字符串是否包含可发音字符(拉丁字母数字、CJK 汉字、假名、韩文音节)。
     *
     * 全是标点或空白的片段没有必要送去 TTS 推理,直接过滤掉能省一次远程调用。
     */
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

    /**
     * 强切分标点:句末级标点,遇到即可安全断句。
     */
    private boolean isStrongTtsCutPunctuation(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；' || c == '\n'
                || c == '!' || c == '?' || c == ';';
    }

    /**
     * 软切分标点:句中停顿,仅在首段或段长足够时才允许在此处切,避免半句抢播产生奇怪停顿。
     */
    private boolean isSoftTtsCutPunctuation(char c) {
        return c == '，' || c == '、' || c == ',' || c == '：' || c == ':';
    }
}
