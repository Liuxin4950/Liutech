package chat.liuxin.ai.service;

import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.config.TtsSegmenterProperties;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.PromptBudget;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import chat.liuxin.ai.dto.AdminArticleDraftSnapshot;
import chat.liuxin.ai.dto.WritingContentPatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
        aiModelPolicy = new AiModelPolicy(aiModelConfigService, aiChatProperties, new PromptBudget(aiChatProperties));
        ttsSegmenter = new TtsSegmenter(new TtsSegmenterProperties());

        service = new AiChatServiceImpl(
                aiChatProperties, siliconFlowChatClient, memoryService, aiMetrics,
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

    private PromptService prepareSyncChat() {
        AiChatProperties properties = new AiChatProperties();
        PromptService prompt = mock(PromptService.class);
        when(prompt.assembleParts(any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PromptService.AssembledPrompt(List.of(), List.of()));
        chatServiceHelper = new ChatServiceHelper(prompt, memoryService, new PromptBudget(properties), null);
        service = new AiChatServiceImpl(properties, siliconFlowChatClient, memoryService, aiMetrics,
                chatServiceHelper, aiModelPolicy, streamingChatService);
        when(aiModelConfigService.getDefaultModel()).thenReturn(Optional.empty());
        return prompt;
    }

    private ChatRequest syncRequest(String message) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setConversationId(99L);
        return request;
    }

    @Test
    void knownModelFailureAfterSavingUserAlsoSavesOneErrorReply() {
        prepareSyncChat();
        AIServiceException.RequestException failure = new AIServiceException.RequestException("输出达到单次上限");
        when(siliconFlowChatClient.chat(anyList(), anyString(), any(), any(), any(), anyString(), anyMap()))
                .thenThrow(failure);
        assertSame(failure, assertThrows(AIServiceException.RequestException.class,
                () -> service.processChat(syncRequest("请回答"), 7L, "USER")));
        var order = inOrder(memoryService);
        order.verify(memoryService).saveUserMessage("7", 99L, "请回答", "fallback-model", null);
        order.verify(memoryService).saveAssistantMessage("7", 99L, null, "fallback-model", MemoryService.MESSAGE_STATUS_ERROR, null);
        verify(memoryService, times(1)).saveAssistantMessage(anyString(), anyLong(), any(), anyString(), anyInt(), any());
    }

    @Test
    void providerStatusFailureAfterSavingUserAlsoSavesErrorReplyAndKeepsStatus() {
        prepareSyncChat();
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "当前繁忙");
        when(siliconFlowChatClient.chat(anyList(), anyString(), any(), any(), any(), anyString(), anyMap()))
                .thenThrow(failure);
        assertSame(failure, assertThrows(ResponseStatusException.class,
                () -> service.processChat(syncRequest("请回答"), 7L, "USER")));
        verify(memoryService, times(1)).saveAssistantMessage("7", 99L, null, "fallback-model", MemoryService.MESSAGE_STATUS_ERROR, null);
    }

    @Test
    void ownerRejectionBeforeSavingUserCannotCreateErrorReply() {
        PromptService prompt = prepareSyncChat();
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问会话");
        when(prompt.assembleParts(any(), any(), any(), anyBoolean(), anyBoolean(), any())).thenThrow(failure);
        assertSame(failure, assertThrows(ResponseStatusException.class,
                () -> service.processChat(syncRequest("请回答"), 7L, "USER")));
        verify(memoryService).getConversationOwnedByUser("7", 99L);
        verifyNoMoreInteractions(memoryService);
        verifyNoInteractions(siliconFlowChatClient);
    }

    @Test
    void initialBudgetRejectionCannotCreateUserOrErrorReply() {
        prepareSyncChat();
        assertThrows(AIServiceException.RequestException.class,
                () -> service.processChat(syncRequest("字".repeat(20_000)), 7L, "USER"));
        verify(memoryService).getConversationOwnedByUser("7", 99L);
        verifyNoMoreInteractions(memoryService);
        verifyNoInteractions(siliconFlowChatClient);
    }

    private ChatRequest writingRequest(String mode) {
        prepareSyncChat();
        var request = syncRequest("只修复正文中的错误");
        request.setDraft(new AdminArticleDraftSnapshot());
        request.getDraft().setContent("<p>不修改的段落</p><p>错字</p>");
        request.setContext(Map.of("requestedFields", List.of("content"), "contentMode", mode));
        return request;
    }

    @Test
    void synchronousWritingReturnsVerifiedPartialProposalWithoutRepeatingWholeArticle() {
        var request = writingRequest("patch");
        when(siliconFlowChatClient.chat(anyList(), anyString(), any(), any(), any(), anyString(), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> context = invocation.getArgument(6);
                    ((WritingContentSession) context.get(WritingContentSession.CONTEXT_KEY))
                            .add(List.of(new WritingContentPatch.Edit("<p>错字</p>", "<p>已改</p>")));
                    return "只修改了一段";
                });
        var response = service.processWriting(request, 7L, "ADMIN");
        assertTrue(response.getSuccess());
        assertEquals("writing", response.getMode());
        assertEquals("只修改了一段", response.getMessage());
        assertEquals(1, response.getFieldUpdates().size());
        assertNull(response.getFieldUpdates().getFirst().getContentHtml());
        assertEquals("<p>已改</p>", response.getFieldUpdates().getFirst().getContentPatch().edits().getFirst().after());
        verifyNoInteractions(memoryService);
    }

    @Test
    void synchronousPatchModeRejectsUnexpectedWholeArticleInsteadOfOverwriting() {
        var request = writingRequest("patch");
        when(siliconFlowChatClient.chat(anyList(), anyString(), any(), any(), any(), anyString(), anyMap()))
                .thenReturn("<p>重写后丢失的整篇</p>");
        var response = service.processWriting(request, 7L, "ADMIN");
        assertFalse(response.getSuccess());
        assertNull(response.getFieldUpdates());
        verifyNoInteractions(memoryService);
    }

    @Test
    void synchronousWholeArticleModeProducesCompleteHtmlProposal() {
        var request = writingRequest("replace");
        when(siliconFlowChatClient.chat(anyList(), anyString(), any(), any(), any(), anyString(), anyMap()))
                .thenReturn("<p>新的完整正文</p>");
        var response = service.processWriting(request, 7L, "ADMIN");
        assertTrue(response.getSuccess());
        assertEquals("<p>新的完整正文</p>", response.getFieldUpdates().getFirst().getContentHtml());
    }

    @Test
    void synchronousExplicitNoChangesCompletesWithoutCreatingAPreview() {
        var request = writingRequest("patch");
        when(siliconFlowChatClient.chat(anyList(), anyString(), any(), any(), any(), anyString(), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> context = invocation.getArgument(6);
                    ((WritingContentSession) context.get(WritingContentSession.CONTEXT_KEY)).add(List.of());
                    return "";
                });
        var response = service.processWriting(request, 7L, "ADMIN");
        assertTrue(response.getSuccess());
        assertTrue(response.getFieldUpdates().isEmpty());
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
