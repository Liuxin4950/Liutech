package chat.liuxin.ai.service;

import chat.liuxin.ai.client.TtsClient;
import chat.liuxin.ai.monitor.AiMetrics;
import chat.liuxin.ai.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AiChatServiceImplTest {

    private AiChatServiceImpl service;
    private Method extractTtsSegmentsMethod;
    private Method extractTtsSegmentsWithModeMethod;
    private Method containsSpeakableTextMethod;

    @BeforeEach
    void setUp() throws Exception {
        service = new AiChatServiceImpl(
                mock(SiliconFlowChatClient.class),
                mock(MemoryService.class),
                mock(AiMetrics.class),
                mock(BlogContextService.class),
                mock(AiModelConfigService.class),
                mock(TtsClient.class)
        );

        extractTtsSegmentsMethod = AiChatServiceImpl.class.getDeclaredMethod("extractTtsSegments", StringBuilder.class);
        extractTtsSegmentsMethod.setAccessible(true);

        extractTtsSegmentsWithModeMethod = AiChatServiceImpl.class.getDeclaredMethod("extractTtsSegments", StringBuilder.class, boolean.class);
        extractTtsSegmentsWithModeMethod.setAccessible(true);

        containsSpeakableTextMethod = AiChatServiceImpl.class.getDeclaredMethod("containsSpeakableText", String.class);
        containsSpeakableTextMethod.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractSegments(String content) throws Exception {
        return (List<String>) extractTtsSegmentsMethod.invoke(service, new StringBuilder(content));
    }

    @SuppressWarnings("unchecked")
    private List<String> extractSegments(String content, boolean firstSegmentSent) throws Exception {
        return (List<String>) extractTtsSegmentsWithModeMethod.invoke(service, new StringBuilder(content), firstSegmentSent);
    }

    private boolean containsSpeakableText(String content) throws Exception {
        return (boolean) containsSpeakableTextMethod.invoke(service, content);
    }

    @Test
    void shouldExtractSegmentAtPunctuationAfterMinimumLength() throws Exception {
        List<String> segments = extractSegments("这是第一句测试内容已经明显超过三十二个字了并且会在句号处稳定切开。后面这一段不足三十二", true);

        assertEquals(1, segments.size());
        assertEquals("这是第一句测试内容已经明显超过三十二个字了并且会在句号处稳定切开。", segments.get(0));
    }

    @Test
    void shouldForceCutWhenNoPunctuationAndContentTooLong() throws Exception {
        String content = "这是一段没有标点符号但是长度会超过八十个字为了验证系统会按照新的后续段硬切分规则稳定地产生一段更长的可用于语音推理的文本并且能够覆盖下一段推理等待时间同时继续补充一些描述内容确保长度真正超过八十个字符";

        List<String> segments = extractSegments(content, true);

        assertEquals(1, segments.size());
        assertEquals(80, segments.get(0).length());
    }

    @Test
    void shouldEmitFirstSegmentEarlierAtComma() throws Exception {
        List<String> segments = extractSegments("你好呀，欢迎来到我的博客，今天聊聊AI。", false);

        assertEquals(1, segments.size());
        assertEquals("你好呀，欢迎来到我的博客，", segments.get(0));
    }

    @Test
    void shouldForceCutFirstSegmentEarlierWhenNoPunctuation() throws Exception {
        List<String> segments = extractSegments("这是第一段没有标点但是应该更快开始朗读", false);

        assertEquals(1, segments.size());
        assertEquals(18, segments.get(0).length());
    }

    @Test
    void shouldNotCutFollowSegmentTooEarlyAtComma() throws Exception {
        List<String> segments = extractSegments("这是一段后续语音内容虽然在前面很快出现了逗号，但是为了避免音频太短需要继续累积更多文字，直到后面这一句足够长再切开。", true);

        assertEquals(1, segments.size());
        assertEquals("这是一段后续语音内容虽然在前面很快出现了逗号，但是为了避免音频太短需要继续累积更多文字，直到后面这一句足够长再切开。", segments.get(0));
    }

    @Test
    void shouldTreatChineseAndLettersAsSpeakableText() throws Exception {
        assertTrue(containsSpeakableText("你好，博客"));
        assertTrue(containsSpeakableText("hello world"));
        assertTrue(containsSpeakableText("GLM-4.6"));
    }

    @Test
    void shouldRejectPurePunctuationAndEmojiAsSpeakableText() throws Exception {
        assertFalse(containsSpeakableText("！！！？？？。。。"));
        assertFalse(containsSpeakableText("😀✨🎉"));
        assertFalse(containsSpeakableText("   "));
    }
}
