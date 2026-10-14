package chat.liuxin.ai.common.tts;

import chat.liuxin.ai.infra.config.TtsSegmenterProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TtsSegmenterTest {
    private final TtsSegmenter segmenter = new TtsSegmenter(new TtsSegmenterProperties());

    @Test
    void shortCompleteSentenceDoesNotWaitForMinimumLengthOrNextSentence() {
        StringBuilder text = new StringBuilder("你好。这里是第二句，还在生成");
        assertEquals(List.of("你好。"), segmenter.extractSegments(text, false));
        assertEquals("这里是第二句，还在生成", text.toString());
    }

    @Test
    void oneLargeChunkKeepsSentenceBoundariesInsteadOfMergingFirstResponse() {
        assertEquals(List.of("你好。", "我来解释一下。", "这是第三句。"), segmenter.splitAll("你好。我来解释一下。这是第三句。"));
    }

    @Test
    void followupCanSplitAtCommaWithoutWaitingForHardCut() {
        String clause = "甲".repeat(40) + "，";
        StringBuilder text = new StringBuilder(clause + "乙".repeat(5));
        assertEquals(List.of(clause), segmenter.extractSegments(text, true));
        assertEquals("乙".repeat(5), text.toString());
    }

    @Test
    void incrementalChunksPreserveAllTextAndDoNotSplitSurrogatePair() {
        String source = "甲".repeat(39) + "😀" + "乙".repeat(80);
        StringBuilder buffer = new StringBuilder();
        StringBuilder received = new StringBuilder();
        boolean sent = false;
        for (int i = 0; i < source.length(); i += 3) {
            buffer.append(source, i, Math.min(source.length(), i + 3));
            for (String part : segmenter.extractSegments(buffer, sent)) {
                assertFalse(Character.isHighSurrogate(part.charAt(part.length() - 1)));
                assertFalse(Character.isLowSurrogate(part.charAt(0)));
                received.append(part);
                sent = true;
            }
        }
        received.append(buffer);
        assertEquals(source, received.toString());
    }
}
