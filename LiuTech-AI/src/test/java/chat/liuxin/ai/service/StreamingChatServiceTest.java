package chat.liuxin.ai.service;

import chat.liuxin.ai.common.client.BlogApiClient;
import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.common.tts.AvatarCueService;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.AdminArticleDraftSnapshot;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.WritingContentPatch;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.config.AvatarCueProperties;
import chat.liuxin.ai.infra.config.TtsSegmenterProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.PromptBudget;
import chat.liuxin.ai.service.tts.TtsSpeechService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StreamingChatServiceTest {
    private final AiModelPolicy.ModelParameters params = new AiModelPolicy.ModelParameters(.3, 1024, 8192, 6656, false, false, "test");
    private final AiChatProperties properties = new AiChatProperties();
    private final SiliconFlowChatClient client = mock(SiliconFlowChatClient.class);
    private final MemoryService memory = mock(MemoryService.class);
    private final ChatServiceHelper helper = mock(ChatServiceHelper.class);
    private final BlogApiClient blog = mock(BlogApiClient.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private StreamingChatService service;
    private final AtomicReference<FluxSink<String>> source = new AtomicReference<>();
    private final CountDownLatch subscribed = new CountDownLatch(1);
    private final CountDownLatch disposed = new CountDownLatch(1);
    private final AtomicReference<Map<String, Object>> toolContext = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        properties.setSseTimeout(10_000);
        properties.getAgent().setMaxConcurrentRequests(2);
        when(helper.prepareMessages(any(), any(), any(), anyBoolean(), anyBoolean(), anyString(), any()))
                .thenReturn(List.of(new UserMessage("input")));
        Flux<String> controlled = Flux.<String>create(sink -> { source.set(sink); subscribed.countDown(); })
                .doOnCancel(disposed::countDown);
        when(client.streamChat(anyList(), anyString(), any(), any(), any(), anyString(), anyMap()))
                .thenAnswer(invocation -> { toolContext.set(invocation.getArgument(6)); return controlled; });
        service = createService();
        service.initStreamExecutor();
    }

    private StreamingChatService createService() {
        return new StreamingChatService(client, memory, helper, mock(TtsSpeechService.class),
                new TtsSegmenter(new TtsSegmenterProperties()), new AvatarCueService(new AvatarCueProperties()),
                properties, new PromptBudget(properties), blog, new AiMetrics(registry));
    }

    @AfterEach
    void close() {
        service.shutdownStreamExecutor();
        registry.close();
    }

    private ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setMessage("请处理");
        request.setConversationId(99L);
        return request;
    }

    private SseEmitter start(boolean writing, Long user) throws Exception {
        SseEmitter emitter = writing ? service.processWritingStream(request(), user, "model", params, "admin")
                : service.processStreamChat(request(), user, "model", params, "user");
        assertTrue(subscribed.await(3, TimeUnit.SECONDS), "模型流必须已订阅");
        return emitter;
    }

    private void callback(SseEmitter emitter, String field) {
        ((Runnable) Objects.requireNonNull(ReflectionTestUtils.getField(emitter, field))).run();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> payloads(SseEmitter emitter) {
        synchronized (emitter) {
            Set<ResponseBodyEmitter.DataWithMediaType> early = (Set<ResponseBodyEmitter.DataWithMediaType>) ReflectionTestUtils.getField(emitter, "earlySendAttempts");
            return early.stream().map(ResponseBodyEmitter.DataWithMediaType::getData).filter(Map.class::isInstance)
                    .map(value -> (Map<String, Object>) value)
                    .map(value -> value.containsKey("version") ? (Map<String, Object>) value.get("data") : value).toList();
        }
    }

    private void finished(String outcome) {
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertEquals(1,
                registry.get("ai_stream_requests_total").tag("outcome", outcome).counter().count()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> writingEvents(SseEmitter emitter) {
        synchronized (emitter) {
            Set<ResponseBodyEmitter.DataWithMediaType> early = (Set<ResponseBodyEmitter.DataWithMediaType>) ReflectionTestUtils.getField(emitter, "earlySendAttempts");
            return early.stream().map(ResponseBodyEmitter.DataWithMediaType::getData).filter(Map.class::isInstance)
                    .map(value -> (Map<String, Object>) value).filter(value -> value.containsKey("version")).toList();
        }
    }

    private ChatRequest patchRequest() {
        ChatRequest request = request();
        request.setContext(Map.of("requestedFields", List.of("content")));
        AdminArticleDraftSnapshot draft = new AdminArticleDraftSnapshot();
        draft.setContent("<p>错误句子</p><!-- 保留原稿注释 --><p>其他段落原样保留</p>");
        request.setDraft(draft);
        return request;
    }

    @Test
    void patchProposalsOnlyContainEditedParagraphsAndTerminalEnvelopeIsLast() throws Exception {
        SseEmitter emitter = service.processWritingStream(patchRequest(), 7L, "model", params, "admin");
        assertTrue(subscribed.await(3, TimeUnit.SECONDS));
        WritingContentSession content = (WritingContentSession) toolContext.get().get(WritingContentSession.CONTEXT_KEY);
        content.add(List.of(new WritingContentPatch.Edit("<p>错误句子</p>", "<p>正确句子</p>")));
        source.get().next("已修正一处句子。");
        source.get().complete();
        finished("success");
        List<Map<String, Object>> events = writingEvents(emitter);
        assertEquals("started", events.getFirst().get("type"));
        assertEquals("completed", events.getLast().get("type"));
        String requestId = (String) events.getFirst().get("requestId");
        for (int i = 0; i < events.size(); i++) {
            assertEquals(1, events.get(i).get("version"));
            assertEquals(requestId, events.get(i).get("requestId"));
            assertEquals(i + 1L, ((Number) events.get(i).get("sequence")).longValue());
        }
        Map<String, Object> proposal = payloads(emitter).stream().filter(map -> map.containsKey("contentPatch")).findFirst().orElseThrow();
        assertEquals(content.revision(), ((WritingContentPatch) proposal.get("contentPatch")).baseRevision());
        assertFalse(proposal.containsKey("contentHtml"));
        assertFalse(proposal.toString().contains("其他段落"));
        assertEquals("<p>正确句子</p><!-- 保留原稿注释 --><p>其他段落原样保留</p>", content.resultHtml());
        assertTrue(payloads(emitter).stream().anyMatch(map -> "validating".equals(map.get("stage")) && "completed".equals(map.get("status"))));
        assertTrue(payloads(emitter).stream().anyMatch(map -> "ready".equals(map.get("stage"))));
        verifyNoInteractions(memory);
    }

    @Test
    void reviewedArticleWithoutEditsCanCompleteWithNoTextAndNoProposal() throws Exception {
        SseEmitter emitter = service.processWritingStream(patchRequest(), 7L, "model", params, "admin");
        assertTrue(subscribed.await(3, TimeUnit.SECONDS));
        WritingContentSession content = (WritingContentSession) toolContext.get().get(WritingContentSession.CONTEXT_KEY);
        content.add(List.of());
        source.get().complete();
        finished("success");
        assertEquals("completed", writingEvents(emitter).getLast().get("type"));
        assertFalse(writingEvents(emitter).stream().anyMatch(event -> "proposal".equals(event.get("type"))));
        assertTrue(payloads(emitter).stream().anyMatch(map -> "检查已完成，正文无需修改".equals(map.get("message"))));
    }

    @Test
    void fullHtmlInPatchModeCannotReplaceExistingDraft() throws Exception {
        SseEmitter emitter = service.processWritingStream(patchRequest(), 7L, "model", params, "admin");
        assertTrue(subscribed.await(3, TimeUnit.SECONDS));
        source.get().next("<p>模型擅自重写全部正文</p>");
        source.get().complete();
        finished("error");
        assertEquals("failed", writingEvents(emitter).getLast().get("type"));
        assertTrue(payloads(emitter).stream().noneMatch(map -> map.containsKey("contentHtml") || map.containsKey("contentPatch")));
    }

    @Test
    void writingDisconnectEndsActivitiesAndDisposesProviderWithoutLateCompletion() throws Exception {
        SseEmitter emitter = start(true, 7L);
        WritingToolEventSink activities = (WritingToolEventSink) toolContext.get().get(WritingToolEventSink.CONTEXT_KEY);
        String activity = activities.fireStart("admin.listCategories", "读取现有分类", null);
        callback(emitter, "completionCallback");
        assertTrue(disposed.await(3, TimeUnit.SECONDS));
        finished("cancelled");
        // Servlet 完成回调时传输已关闭，取消状态保留在活动生命周期，不能伪造可送达事件。
        assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(activities, "active")).isEmpty());
        activities.complete(activity, "迟到的结果");
        source.get().complete();
        assertFalse(writingEvents(emitter).stream().anyMatch(event -> "completed".equals(event.get("type"))));
        verifyNoInteractions(memory);
    }

    @Test
    void schedulingFailureStillStartsUnifiedRequestBeforeItsFailure() {
        service.shutdownStreamExecutor();
        SseEmitter emitter = service.processWritingStream(request(), 7L, "model", params, "admin");
        List<Map<String, Object>> events = writingEvents(emitter);
        assertEquals(List.of("started", "failed"), events.stream().map(event -> event.get("type")).toList());
        assertEquals(events.getFirst().get("requestId"), events.getLast().get("requestId"));
        assertEquals(1L, ((Number) events.getFirst().get("sequence")).longValue());
        assertEquals(2L, ((Number) events.getLast().get("sequence")).longValue());
        verifyNoInteractions(client, memory);
    }

    @Test
    void disconnectDisposesUpstreamAndPersistsPartialOnlyOnce() throws Exception {
        SseEmitter emitter = start(false, 7L);
        source.get().next("已生成的部分");
        callback(emitter, "completionCallback");
        assertTrue(disposed.await(3, TimeUnit.SECONDS));
        callback(emitter, "timeoutCallback");
        verify(memory, times(1)).saveAssistantMessage("7", 99L, "已生成的部分", "model", MemoryService.MESSAGE_STATUS_ERROR, null);
        verify(memory, never()).saveAssistantMessage(anyString(), anyLong(), any(), anyString(), eq(MemoryService.MESSAGE_STATUS_NORMAL), any());
        finished("cancelled");
    }

    @Test
    void applicationDeadlineNotifiesBeforeCompletingAndCancelsUpstream() throws Exception {
        service.shutdownStreamExecutor();
        properties.setSseTimeout(500);
        service = createService();
        service.initStreamExecutor();
        SseEmitter emitter = start(false, 7L);
        source.get().next("部分");
        assertTrue(disposed.await(3, TimeUnit.SECONDS));
        finished("timeout");
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertTrue(payloads(emitter).stream()
                .anyMatch(map -> String.valueOf(map.get("error")).contains("超时"))));
        verify(memory, times(1)).saveAssistantMessage("7", 99L, "部分", "model", MemoryService.MESSAGE_STATUS_ERROR, null);
    }

    @Test
    void cancellationBeforeSubscriptionHandleArrivesStillDisposesIt() throws Exception {
        CompletableFuture<Void> release = new CompletableFuture<>();
        CountDownLatch subscribing = new CountDownLatch(1);
        Flux<String> delayed = Flux.<String>never().doOnSubscribe(ignored -> { subscribing.countDown(); release.join(); })
                .doOnCancel(disposed::countDown);
        when(client.streamChat(anyList(), anyString(), any(), any(), any(), anyString(), anyMap())).thenReturn(delayed);
        SseEmitter emitter = service.processStreamChat(request(), 7L, "model", params, "user");
        try {
            assertTrue(subscribing.await(3, TimeUnit.SECONDS));
            callback(emitter, "completionCallback");
        } finally { release.complete(null); }
        assertTrue(disposed.await(3, TimeUnit.SECONDS));
        verify(memory, times(1)).saveAssistantMessage("7", 99L, null, "model", MemoryService.MESSAGE_STATUS_ERROR, null);
    }

    @Test
    void truncatedWritingNeverEmitsArticleBodyUpdate() throws Exception {
        SseEmitter emitter = start(true, 7L);
        source.get().next("<p>" + "正文".repeat(150) + "</p>");
        assertTrue(payloads(emitter).stream().noneMatch(map -> map.containsKey("contentHtml")));
        source.get().error(new AIServiceException.RequestException("输出达到长度上限，正文未完成"));
        finished("error");
        assertTrue(payloads(emitter).stream().noneMatch(map -> map.containsKey("contentHtml")));
        assertTrue(payloads(emitter).stream().anyMatch(map -> map.containsKey("error")));
        verifyNoInteractions(memory);
    }

    @Test
    void onlyCompleteArticleHtmlIsReturnedAfterNormalCompletion() throws Exception {
        SseEmitter emitter = start(true, 7L);
        String html = "<h2>标题</h2><p>" + "正文".repeat(150) + "</p>";
        source.get().next(html);
        assertTrue(payloads(emitter).stream().noneMatch(map -> map.containsKey("contentHtml")));
        source.get().complete();
        finished("success");
        assertEquals(1, payloads(emitter).stream().filter(map -> html.equals(map.get("contentHtml"))).count());
    }

    @Test
    void completeShortBodyIsReturnedWithoutMinimumLength() throws Exception {
        ChatRequest request = request();
        request.setContext(Map.of("requestedFields", List.of("content")));
        SseEmitter emitter = service.processWritingStream(request, 7L, "model", params, "admin");
        assertTrue(subscribed.await(3, TimeUnit.SECONDS));
        String html = "<p>今晚网站维护，预计十分钟恢复。</p>";
        source.get().next(html);
        source.get().complete();
        finished("success");
        assertEquals(1, payloads(emitter).stream().filter(map -> html.equals(map.get("contentHtml"))).count());
        assertTrue(payloads(emitter).stream().noneMatch(map -> map.containsKey("error")));
    }

    @Test
    void existingCompleteMediaNodesSurviveWritingBodyValidation() throws Exception {
        String media = "<audio controls><source src='/uploads/demo.mp3' type='audio/mpeg'><track src='/uploads/captions.vtt'></audio>"
                + "<video controls poster='/uploads/poster.png'><source src='/uploads/demo.mp4'></video>"
                + "<iframe src='https://player.example/embed/1' title='原视频'></iframe>"
                + "<svg viewBox='0 0 10 10'><svg><circle cx='5' cy='5' r='4'></circle></svg></svg>"
                + "<math><mrow><mi>x</mi><mo>=</mo><mn>1</mn></mrow></math>";
        ChatRequest request = request();
        request.setContext(Map.of("requestedFields", List.of("content"), "contentMode", "replace"));
        AdminArticleDraftSnapshot draft = new AdminArticleDraftSnapshot();
        draft.setContent("<p>原稿说明</p>" + media);
        request.setDraft(draft);
        SseEmitter emitter = service.processWritingStream(request, 7L, "model", params, "admin");
        assertTrue(subscribed.await(3, TimeUnit.SECONDS));
        String html = "<p>修改后的说明</p>" + media;
        source.get().next(html);
        source.get().complete();
        finished("success");
        assertEquals(1, payloads(emitter).stream().filter(map -> html.equals(map.get("contentHtml"))).count());
    }

    @Test
    void newOrChangedMediaCannotUseExistingDraftException() {
        String existing = "<video controls><source src='/uploads/original.mp4'></video>";
        assertNull(ReflectionTestUtils.invokeMethod(service, "completeArticleHtml",
                "<p>正文</p><video controls><source src='/uploads/unknown.mp4'></video>", existing));
        assertNull(ReflectionTestUtils.invokeMethod(service, "completeArticleHtml",
                "<p>正文</p><iframe src='https://unknown.example/'></iframe>", existing));
        assertNull(ReflectionTestUtils.invokeMethod(service, "completeArticleHtml",
                "<p>正文</p><video controls><source src='/uploads/original.mp4'>", existing));
        assertNull(ReflectionTestUtils.invokeMethod(service, "completeArticleHtml",
                "<p>正文</p><audio src='/uploads/new.mp3'></audio>", null));
    }

    @Test
    void validComparisonTextInsideCodeDoesNotRequireEscapingGreaterThan() {
        String html = "<pre><code>if (count > 0) return count;</code></pre>";
        assertEquals(html, ReflectionTestUtils.invokeMethod(service, "completeArticleHtml", html));
    }

    @Test
    void plainAdviceMalformedHtmlAndUnsafeHtmlCannotBecomeArticleUpdates() {
        for (String text : List.of("普通建议".repeat(100), "<p>" + "正文".repeat(150),
                "说明如下：<p>" + "正文".repeat(150) + "</p>",
                "<p onclick='alert(1)'>" + "正文".repeat(150) + "</p>")) {
            assertNull(ReflectionTestUtils.invokeMethod(service, "completeArticleHtml", text));
        }
    }

    @Test
    void requestedBodyWithIncompleteHtmlFailsWithoutWritingIt() throws Exception {
        ChatRequest request = request();
        request.setContext(Map.of("requestedFields", List.of("content")));
        SseEmitter emitter = service.processWritingStream(request, 7L, "model", params, "admin");
        assertTrue(subscribed.await(3, TimeUnit.SECONDS));
        source.get().next("<p>" + "正文".repeat(150));
        source.get().complete();
        finished("error");
        assertTrue(payloads(emitter).stream().noneMatch(map -> map.containsKey("contentHtml")));
        assertTrue(payloads(emitter).stream().anyMatch(map -> String.valueOf(map.get("error")).contains("完整有效")));
    }

    @Test
    void titleOnlyRequestCannotReceiveBodyPreviewEvenWhenModelOutputsCompleteHtml() throws Exception {
        ChatRequest request = request();
        request.setContext(Map.of("requestedFields", List.of("title")));
        SseEmitter emitter = service.processWritingStream(request, 7L, "model", params, "admin");
        assertTrue(subscribed.await(3, TimeUnit.SECONDS));
        assertEquals(List.of("title"), toolContext.get().get("allowedWritingFields"));
        source.get().next("<p>" + "不应改写正文".repeat(50) + "</p>");
        source.get().complete();
        finished("success");
        assertTrue(payloads(emitter).stream().noneMatch(map -> map.containsKey("contentHtml")));
    }

    @Test
    void checkOnlyRequestCannotProduceAnyArticleBodyModification() throws Exception {
        ChatRequest request = request();
        request.setContext(Map.of("requestedFields", List.of("check")));
        SseEmitter emitter = service.processWritingStream(request, 7L, "model", params, "admin");
        assertTrue(subscribed.await(3, TimeUnit.SECONDS));
        assertEquals(List.of("check"), toolContext.get().get("allowedWritingFields"));
        source.get().next("<p>" + "检查意见".repeat(60) + "</p>");
        source.get().complete();
        finished("success");
        assertTrue(payloads(emitter).stream().noneMatch(map -> map.containsKey("contentHtml")));
        assertTrue(payloads(emitter).stream().anyMatch(map -> "ready".equals(map.get("stage"))
                && String.valueOf(map.get("message")).contains("草稿未修改")));
        assertFalse(payloads(emitter).stream().anyMatch(map -> "ready".equals(map.get("stage"))
                && String.valueOf(map.get("message")).contains("采纳")));
    }

    @Test
    void closingAfterModelCompletionDoesNotInterruptOrDuplicateNormalPersistence() throws Exception {
        CountDownLatch saving = new CountDownLatch(1);
        CompletableFuture<Void> release = new CompletableFuture<>();
        doAnswer(ignored -> { saving.countDown(); release.join(); return null; }).when(memory)
                .saveAssistantMessage("7", 99L, "完整回答", "model", MemoryService.MESSAGE_STATUS_NORMAL, null);
        SseEmitter emitter = start(false, 7L);
        source.get().next("完整回答");
        CompletableFuture<Void> completion = CompletableFuture.runAsync(() -> source.get().complete());
        try {
            assertTrue(saving.await(3, TimeUnit.SECONDS));
            callback(emitter, "completionCallback");
        } finally { release.complete(null); }
        completion.get(3, TimeUnit.SECONDS);
        finished("success");
        verify(memory, times(1)).saveAssistantMessage("7", 99L, "完整回答", "model", MemoryService.MESSAGE_STATUS_NORMAL, null);
        verify(memory, never()).saveAssistantMessage(anyString(), anyLong(), any(), anyString(), eq(MemoryService.MESSAGE_STATUS_ERROR), any());
        assertNull(registry.find("ai_stream_requests_total").tag("outcome", "cancelled").counter());
    }

    @Test
    void successRecordsFirstContentLatencyAndRealUsageAndDoesNotBecomeCancelled() throws Exception {
        SseEmitter emitter = start(false, 7L);
        AiMetrics.UsageTracker usage = (AiMetrics.UsageTracker) toolContext.get().get(AiMetrics.UsageTracker.CONTEXT_KEY);
        usage.record(120, 30);
        usage.record(160, 20);
        assertSame(params, toolContext.get().get(SiliconFlowChatClient.MODEL_PARAMETERS_CONTEXT_KEY));
        source.get().next("回答");
        source.get().complete();
        finished("success");
        callback(emitter, "completionCallback");
        assertEquals(1, registry.get("ai_stream_first_token_seconds").timer().count());
        assertEquals(280, registry.get("ai_tokens_total").tag("direction", "input").counter().count());
        assertEquals(50, registry.get("ai_tokens_total").tag("direction", "output").counter().count());
        assertEquals(0, registry.get("ai_stream_active_requests").gauge().value());
        assertNull(registry.find("ai_stream_requests_total").tag("outcome", "cancelled").counter());
        verify(memory, times(1)).saveAssistantMessage("7", 99L, "回答", "model", MemoryService.MESSAGE_STATUS_NORMAL, null);
    }

    @Test
    void forgedArticleIdIsDroppedAndRealCardUsesBackendTitle() throws Exception {
        PostDetailDTO real = new PostDetailDTO();
        real.setId(15L); real.setTitle("博客真实标题");
        when(blog.getPostDetail(15L)).thenReturn(real);
        SseEmitter emitter = start(false, null);
        source.get().next("[模型捏造标题](/post/15) [不存在文章](/post/999)");
        source.get().complete();
        finished("success");
        Map<String, Object> cards = payloads(emitter).stream().filter(map -> map.containsKey("items")).findFirst().orElseThrow();
        List<?> items = (List<?>) cards.get("items");
        assertEquals(1, items.size());
        assertEquals("博客真实标题", ((chat.liuxin.ai.dto.PostSummaryDTO) items.getFirst()).getTitle());
    }

    @Test
    void admissionLimitCountsOpenModelStreamsAndReleasesAfterCancel() throws Exception {
        service.shutdownStreamExecutor();
        properties.getAgent().setMaxConcurrentRequests(1);
        service = createService();
        service.initStreamExecutor();
        SseEmitter first = start(false, null);
        assertThrows(ResponseStatusException.class, () -> service.processStreamChat(request(), null, "model", params, "guest"));
        callback(first, "completionCallback");
        assertDoesNotThrow(() -> service.processStreamChat(request(), null, "model", params, "guest"));
    }
}
