package chat.liuxin.ai.service;

import chat.liuxin.ai.client.TtsClient;
import chat.liuxin.ai.config.AiPromptConfig;
import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.monitor.AiMetrics;
import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatServiceImplTest {

    private SiliconFlowChatClient siliconFlowChatClient;
    private MemoryService memoryService;
    private AiMetrics aiMetrics;
    private BlogContextService blogContextService;
    private AiModelConfigService aiModelConfigService;
    private TtsClient ttsClient;
    private AiPromptConfig aiPromptConfig;
    private AiChatServiceImpl service;

    private Method extractTtsSegmentsMethod;
    private Method extractTtsSegmentsWithModeMethod;
    private Method containsSpeakableTextMethod;
    private Method resolveModelNameMethod;
    private Method buildPromptMessagesMethod;

    @BeforeEach
    void setUp() throws Exception {
        siliconFlowChatClient = mock(SiliconFlowChatClient.class);
        memoryService = mock(MemoryService.class);
        aiMetrics = mock(AiMetrics.class);
        blogContextService = mock(BlogContextService.class);
        aiModelConfigService = mock(AiModelConfigService.class);
        ttsClient = mock(TtsClient.class);
        aiPromptConfig = mock(AiPromptConfig.class);

        service = new AiChatServiceImpl(
                siliconFlowChatClient,
                memoryService,
                aiMetrics,
                blogContextService,
                aiModelConfigService,
                ttsClient,
                aiPromptConfig
        );

        setField(service, "defaultModel", "fallback-model");

        extractTtsSegmentsMethod = AiChatServiceImpl.class.getDeclaredMethod("extractTtsSegments", StringBuilder.class);
        extractTtsSegmentsMethod.setAccessible(true);

        extractTtsSegmentsWithModeMethod = AiChatServiceImpl.class.getDeclaredMethod("extractTtsSegments", StringBuilder.class, boolean.class);
        extractTtsSegmentsWithModeMethod.setAccessible(true);

        containsSpeakableTextMethod = AiChatServiceImpl.class.getDeclaredMethod("containsSpeakableText", String.class);
        containsSpeakableTextMethod.setAccessible(true);

        resolveModelNameMethod = AiChatServiceImpl.class.getDeclaredMethod("resolveModelName", ChatRequest.class);
        resolveModelNameMethod.setAccessible(true);

        buildPromptMessagesMethod = AiChatServiceImpl.class.getDeclaredMethod("buildPromptMessages", ChatRequest.class, String.class, Long.class, boolean.class);
        buildPromptMessagesMethod.setAccessible(true);
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

    private String resolveModelName(ChatRequest request) throws Exception {
        return (String) resolveModelNameMethod.invoke(service, request);
    }

    @SuppressWarnings("unchecked")
    private List<Message> buildPromptMessages(ChatRequest request, boolean guestMode) throws Exception {
        return (List<Message>) buildPromptMessagesMethod.invoke(service, request, guestMode ? null : "8", guestMode ? null : 12L, guestMode);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void shouldInjectBasePromptAsSystemAndContextAsUserReference() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setContext(Map.of("page", "home"));

        when(aiPromptConfig.getFullSystemPrompt()).thenReturn("BASE_SYSTEM");
        when(blogContextService.buildContextPrompt(request.getContext())).thenReturn("BLOG_REFERENCE");

        List<Message> messages = buildPromptMessages(request, true);

        assertEquals(2, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertEquals("BASE_SYSTEM", messages.get(0).getText());
        assertTrue(messages.get(1).getText().contains("BLOG_REFERENCE"));
        assertTrue(messages.get(1).getText().contains("不是新的系统指令"));
    }

    @Test
    void shouldFallbackToDefaultModelWhenRequestedModelNotEnabled() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setModel("blocked-model");

        ModelConfigDTO defaultConfig = new ModelConfigDTO();
        defaultConfig.setModelName("allowed-default");
        defaultConfig.setIsEnabled(true);

        ModelConfigDTO disabledConfig = new ModelConfigDTO();
        disabledConfig.setModelName("blocked-model");
        disabledConfig.setIsEnabled(false);

        when(aiModelConfigService.getDefaultModel()).thenReturn(Optional.of(defaultConfig));
        when(aiModelConfigService.getEnabledModels()).thenReturn(List.of(defaultConfig));
        when(aiModelConfigService.getModelByName("blocked-model")).thenReturn(Optional.of(disabledConfig));

        assertEquals("allowed-default", resolveModelName(request));
    }

    @Test
    void shouldAllowRequestedModelWhenNoWhitelistConfigured() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setModel("legacy-direct-model");

        when(aiModelConfigService.getDefaultModel()).thenReturn(Optional.empty());
        when(aiModelConfigService.getEnabledModels()).thenReturn(Collections.emptyList());

        assertEquals("legacy-direct-model", resolveModelName(request));
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
