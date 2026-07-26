package chat.liuxin.ai.service;

import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.config.TtsSegmenterProperties;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatServiceImplTest {

    private SiliconFlowChatClient siliconFlowChatClient;
    private MemoryService memoryService;
    private AiMetrics aiMetrics;
    private AiModelConfigService aiModelConfigService;
    private ChatServiceHelper chatServiceHelper;
    private AiModelPolicy aiModelPolicy;
    private TtsSegmenter ttsSegmenter;
    private StreamingChatService streamingChatService;
    private AiChatServiceImpl service;
    private Method resolveModelNameMethod;

    @BeforeEach
    void setUp() throws Exception {
        siliconFlowChatClient = mock(SiliconFlowChatClient.class);
        memoryService = mock(MemoryService.class);
        aiMetrics = mock(AiMetrics.class);
        aiModelConfigService = mock(AiModelConfigService.class);
        chatServiceHelper = mock(ChatServiceHelper.class);
        streamingChatService = mock(StreamingChatService.class);
        AiChatProperties aiChatProperties = new AiChatProperties();
        aiChatProperties.setDefaultModel("fallback-model");
        aiModelPolicy = new AiModelPolicy(aiModelConfigService, aiChatProperties);
        ttsSegmenter = new TtsSegmenter(new TtsSegmenterProperties());

        service = new AiChatServiceImpl(
                siliconFlowChatClient, memoryService, aiMetrics,
                chatServiceHelper, aiModelPolicy, streamingChatService
        );

        resolveModelNameMethod = AiChatServiceImpl.class.getDeclaredMethod("resolveModelName", ChatRequest.class);
        resolveModelNameMethod.setAccessible(true);
    }

    private List<String> extractSegments(String content, boolean firstSegmentSent) {
        return ttsSegmenter.extractSegments(new StringBuilder(content), firstSegmentSent);
    }

    private boolean containsSpeakableText(String content) {
        return ttsSegmenter.containsSpeakableText(content);
    }

    private String resolveModelName(ChatRequest request) throws Exception {
        return (String) resolveModelNameMethod.invoke(service, request);
    }

    @Test
    void shouldReturnDbDefaultModel() throws Exception {
        ChatRequest request = new ChatRequest();

        ModelConfigDTO defaultConfig = new ModelConfigDTO();
        defaultConfig.setModelName("allowed-default");
        defaultConfig.setIsEnabled(true);

        when(aiModelConfigService.getDefaultModel()).thenReturn(Optional.of(defaultConfig));

        assertEquals("allowed-default", resolveModelName(request));
    }

    @Test
    void shouldFallbackToYmlDefaultWhenNoDbDefault() throws Exception {
        ChatRequest request = new ChatRequest();

        when(aiModelConfigService.getDefaultModel()).thenReturn(Optional.empty());

        assertEquals("fallback-model", resolveModelName(request));
    }

    @Test
    void shouldExtractSegmentAtPunctuationAfterMinimumLength() throws Exception {
        List<String> segments = extractSegments("这是第一句测试内容已经明显超过六十个字了会在句号处稳定切开并且保持更长的连续播报效果避免下一段还没推理好就已经播完了同时再补充一些上下文信息保证触发当前后续段阈值。后面这一段不足六十", true);

        assertEquals(1, segments.size());
        assertEquals("这是第一句测试内容已经明显超过六十个字了会在句号处稳定切开并且保持更长的连续播报效果避免下一段还没推理好就已经播完了同时再补充一些上下文信息保证触发当前后续段阈值。", segments.get(0));
    }

    @Test
    void shouldForceCutWhenNoPunctuationAndContentTooLong() throws Exception {
        String content = "这是一段没有标点符号但是长度会超过八十个字为了验证系统会按照新的后续段硬切分规则稳定地产生一段更长的可用于语音推理的文本并且能够覆盖下一段推理等待时间同时继续补充一些描述内容确保长度真正超过八十个字符";

        List<String> segments = extractSegments(content, true);

        assertEquals(1, segments.size());
        assertEquals(100, segments.get(0).length());
    }

    @Test
    void shouldEmitFirstSegmentEarlierAtComma() throws Exception {
        List<String> segments = extractSegments("你好呀欢迎来到我的个人技术博客这里会分享很多实践经验，后面还会继续展开更多工程化内容", false);

        assertEquals(1, segments.size());
        assertEquals("你好呀欢迎来到我的个人技术博客这里会分享很多实践经验，", segments.get(0));
    }

    @Test
    void shouldForceCutFirstSegmentEarlierWhenNoPunctuation() throws Exception {
        List<String> segments = extractSegments("这是第一段没有标点但是应该更快开始朗读并且要超过四十个字才能触发当前的首段硬切规则确保行为符合现在的配置", false);

        assertEquals(1, segments.size());
        assertEquals(40, segments.get(0).length());
    }

    @Test
    void shouldNotCutFollowSegmentTooEarlyAtComma() throws Exception {
        List<String> segments = extractSegments("这是一段后续语音内容虽然在前面很快出现了逗号，但是为了避免音频太短需要继续累积更多文字和信息密度直到超过六十个字以后再观察切分点最后在这一句完整结束时再切开。", true);

        assertEquals(1, segments.size());
        assertEquals("这是一段后续语音内容虽然在前面很快出现了逗号，但是为了避免音频太短需要继续累积更多文字和信息密度直到超过六十个字以后再观察切分点最后在这一句完整结束时再切开。", segments.get(0));
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
