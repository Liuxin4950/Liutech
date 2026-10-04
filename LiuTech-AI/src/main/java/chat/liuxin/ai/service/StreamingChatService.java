package chat.liuxin.ai.service;

import chat.liuxin.ai.service.tts.TtsSpeechService;
import chat.liuxin.ai.common.client.BlogApiClient;
import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.common.tts.AvatarCueService;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import chat.liuxin.ai.dto.AvatarCuePayload;
import chat.liuxin.ai.dto.FieldUpdatePayload;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import chat.liuxin.ai.common.mcp.ToolResultBudget;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.infra.security.PromptBudget;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 聊天/写作 SSE 生命周期；关闭浏览器或超时会取消同一次模型订阅。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingChatService {
    private static final long HEARTBEAT_INTERVAL_SEC = 15;
    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    private final ChatServiceHelper chatServiceHelper;
    private final TtsSpeechService ttsSpeechService;
    private final TtsSegmenter ttsSegmenter;
    private final AvatarCueService avatarCueService;
    private final AiChatProperties aiChatProperties;
    private final PromptBudget promptBudget;
    private final BlogApiClient blogApiClient;
    private final AiMetrics aiMetrics;
    private ThreadPoolExecutor streamExecutor;
    private ScheduledExecutorService heartbeatExecutor;
    private Semaphore streamPermits;
    private final Set<StreamSession> activeSessions = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void initStreamExecutor() {
        int concurrency = Math.max(1, aiChatProperties.getAgent().getMaxConcurrentRequests());
        int queueSize = Math.max(1, aiChatProperties.getAgent().getMaxQueuedRequests());
        streamPermits = new Semaphore(concurrency);
        streamExecutor = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize), new ThreadPoolExecutor.AbortPolicy());
        heartbeatExecutor = Executors.newScheduledThreadPool(Math.min(4, concurrency));
    }

    @PreDestroy
    void shutdownStreamExecutor() {
        for (StreamSession session : activeSessions) session.stop("cancelled", null, false);
        SseEmitterHelper.shutdown(heartbeatExecutor, true);
        SseEmitterHelper.shutdown(streamExecutor, true);
    }

    public SseEmitter processStreamChat(ChatRequest request, Long userId, String modelName,
                                        AiModelPolicy.ModelParameters params, String role) {
        Long conversationId = userId == null ? null : request.getConversationId();
        if (conversationId != null) memoryService.getConversationOwnedByUser(userId.toString(), conversationId);
        return start(request, userId, modelName, params, role, false);
    }

    public SseEmitter processWritingStream(ChatRequest request, Long userId, String modelName,
                                           AiModelPolicy.ModelParameters params, String role) {
        return start(request, userId, modelName, params, role, true);
    }

    private SseEmitter start(ChatRequest request, Long userId, String modelName,
                             AiModelPolicy.ModelParameters params, String role, boolean writingMode) {
        if (!streamPermits.tryAcquire()) {
            aiMetrics.recordFailure(modelName, 0, "capacity");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 当前处理的请求较多，请稍后重试");
        }
        StreamSession session = new StreamSession(userId, modelName, writingMode,
                userId == null ? null : request.getConversationId());
        session.originalDraftContent = writingMode && request.getDraft() != null ? request.getDraft().getContent() : null;
        Object requestedFields = request.getContext() == null ? null : request.getContext().get("requestedFields");
        session.contentRequested = requestedFields instanceof List<?> fields
                && fields.contains("content") && !fields.contains("check");
        activeSessions.add(session);
        session.emitter.onCompletion(() -> session.stop("cancelled", null, false));
        session.emitter.onError(error -> session.stop("cancelled", error, false));
        session.emitter.onTimeout(() -> session.stop("timeout", null, true));
        try {
            // Servlet 自身的 timeout 回调执行前已把 emitter 标记完成，无法再推送错误；
            // 应用 deadline 先通知，Servlet 稍后只作兜底。
            session.setDeadline(heartbeatExecutor.schedule(() -> session.stop("timeout", null, true),
                    Math.max(1, aiChatProperties.getSseTimeout()), TimeUnit.MILLISECONDS));
            Future<?> future = streamExecutor.submit(() -> prepareAndSubscribe(session, request, params, role));
            session.setFuture(future);
        } catch (RejectedExecutionException error) {
            session.stop("error", error, true);
        }
        return session.emitter;
    }

    private void prepareAndSubscribe(StreamSession session, ChatRequest request,
                                      AiModelPolicy.ModelParameters params, String role) {
        session.preparationThread = Thread.currentThread();
        try {
            if (!session.isRunning()) return;
            List<Message> messages = chatServiceHelper.prepareMessages(request, session.userId,
                    session.conversationId.get(), session.guestMode, session.writingMode, session.modelName, params);
            // 会话与用户消息变动和取消共用短临界区，避免取消后又落一条没有回复的用户消息。
            synchronized (session) {
                if (!session.isRunning()) return;
                if (!session.guestMode && !session.writingMode) {
                    Long id = session.conversationId.get();
                    if (id == null) {
                        id = memoryService.createConversation(session.userId,
                                chatServiceHelper.generateTitle(request.getMessage()));
                        session.conversationId.set(id);
                    }
                    memoryService.saveUserMessage(session.userId, id, request.getMessage(), session.modelName, null);
                    session.userMessageSaved = true;
                }
            }
            if (!session.isRunning()) return;
            session.send("start", SseEmitterHelper.eventPayload("conversationId", session.conversationId.get(),
                    "model", session.modelName, "mode", session.writingMode ? "writing" : session.guestMode ? "guest" : "user"));
            session.setHeartbeat(heartbeatExecutor.scheduleAtFixedRate(() -> {
                if (session.closed.get()) return;
                try {
                    session.send("heartbeat", SseEmitterHelper.eventPayload("conversationId", session.conversationId.get(),
                            "timestamp", System.currentTimeMillis()));
                } catch (Exception error) {
                    session.stop("cancelled", error, false);
                }
            }, HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS));

            Map<String, Object> toolContext = new HashMap<>();
            toolContext.put(AiMetrics.UsageTracker.CONTEXT_KEY, session.usage);
            toolContext.put(SiliconFlowChatClient.MODEL_PARAMETERS_CONTEXT_KEY, params);
            // prepareMessages 已校验字段白名单；该范围只能收窄写作入口原有权限。
            Object requestedFields = request.getContext() == null ? null : request.getContext().get("requestedFields");
            if (session.writingMode && requestedFields instanceof List<?> fields && !fields.isEmpty()) {
                session.allowedWritingFields = fields.stream().map(String.class::cast).toList();
                toolContext.put("allowedWritingFields", session.allowedWritingFields);
            }
            toolContext.put(ToolResultBudget.CONTEXT_KEY, promptBudget.toolResultCharBudget(
                    params.inputBudgetTokens(), aiChatProperties.getAgent().getMaxToolResultChars()));
            if (session.writingMode) {
                toolContext.put(FieldUpdateCollector.CONTEXT_KEY, session.collector);
                session.collector.addListener(payload -> {
                    if (!session.isRunning()) return;
                    // 字段只在前端作为待采用预览；正文不接受工具或旧文本协议回写。
                    Map<String, Object> update = toPayloadMap(payload);
                    if (!update.isEmpty()) {
                        try { session.send("field-update", update); }
                        catch (Exception error) { session.stop("cancelled", error, false); }
                    }
                });
                toolContext.put(WritingToolEventSink.CONTEXT_KEY, new WritingToolEventSink((event, payload) -> {
                    if (!session.isRunning()) return;
                    try { session.send(event, payload); }
                    catch (Exception error) { session.stop("cancelled", error, false); }
                }));
            }
            if (!session.isRunning()) return;
            session.ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());
            if (session.ttsEnabled) {
                session.setTtsExecutor(Executors.newFixedThreadPool(Math.max(1, aiChatProperties.getTtsStreamConcurrency())));
            }
            Flux<String> flux = siliconFlowChatClient.streamChat(messages, session.modelName,
                    params.temperature(), params.maxTokens(), session.writingMode ? SiliconFlowChatClient.ChatMode.WRITING
                            : SiliconFlowChatClient.ChatMode.CHAT, role, toolContext);
            if (!session.isRunning()) return;
            session.setSubscription(flux.subscribe(chunk -> handleChunk(session, chunk),
                    error -> session.stop("error", error, true), () -> completeGeneration(session)));
        } catch (Exception error) {
            session.stop("error", error, true);
        }
    }

    private void handleChunk(StreamSession session, String chunk) {
        if (!session.isRunning() || chunk == null || chunk.isEmpty()) return;
        try {
            List<String> dataTexts;
            if (session.writingMode) {
                // 旧文本标记只用于剥离；不允许模型文字绕过结构化工具校验修改字段。
                dataTexts = session.parser.feed(chunk).dataTexts();
            } else {
                dataTexts = List.of(chunk);
            }
            for (String dataText : dataTexts) {
                if (dataText.isEmpty() || !session.isRunning()) continue;
                session.append(dataText);
                if (session.firstToken.compareAndSet(false, true)) {
                    aiMetrics.recordStreamFirstToken(session.modelName, session.mode(), session.elapsedMillis());
                }
                session.textBuffer.append(dataText);
                sendSegments(session, ttsSegmenter.extractSegments(session.textBuffer, session.seq.get() > 0));
                session.send("data", SseEmitterHelper.eventPayload("content", dataText, "conversationId", session.conversationId.get()));
            }
        } catch (Exception error) {
            session.stop("cancelled", error, false);
        }
    }

    private void completeGeneration(StreamSession session) {
        // 正常模型完成先占用唯一终态：后续浏览器关闭不能把已完成内容改成错误或重复落库。
        if (!session.modelEnded.compareAndSet(false, true)) return;
        try {
            if (session.writingMode) {
                String rest = session.parser.flush();
                if (rest != null && !rest.isEmpty()) {
                    session.append(rest);
                    session.textBuffer.append(rest);
                    session.send("data", SseEmitterHelper.eventPayload("content", rest, "conversationId", session.conversationId.get()));
                }
            }
            String fullResponse = session.responseText();
            if (fullResponse.isBlank() && (!session.writingMode || session.collector.isEmpty())) {
                throw new AIServiceException.ModelException("AI 没有返回有效内容，请稍后重试");
            }
            String articleHtml = session.writingMode && session.allowsBodyPreview()
                    ? completeArticleHtml(fullResponse, session.originalDraftContent) : null;
            if (session.writingMode && session.contentRequested && articleHtml == null) {
                throw new AIServiceException.ModelException("AI 没有返回完整有效的 HTML 正文，草稿正文未修改，请重试");
            }
            if (!session.guestMode && !session.writingMode) {
                memoryService.saveAssistantMessage(session.userId, session.conversationId.get(), fullResponse,
                        session.modelName, MemoryService.MESSAGE_STATUS_NORMAL, null);
                session.assistantSaved.set(true);
            }
            session.finishMetrics("success");
            if (session.closed.get()) return;
            if (session.writingMode) {
                if (articleHtml != null) {
                    session.send("field-update", SseEmitterHelper.eventPayload("contentHtml", articleHtml, "fields", List.of("content")));
                }
            }
            sendSegments(session, ttsSegmenter.extractSegments(session.textBuffer, session.seq.get() > 0));
            String rest = session.textBuffer.toString().trim();
            session.textBuffer.setLength(0);
            if (!rest.isEmpty()) sendSegments(session, List.of(rest));
            List<PostSummaryDTO> articles = extractArticleResults(fullResponse);
            if (!articles.isEmpty()) {
                session.send("article-results", SseEmitterHelper.eventPayload("items", articles,
                        "reason", "这些文章已在博客中核实，可以点开阅读。"));
            }
            session.send("complete", SseEmitterHelper.eventPayload("conversationId", session.conversationId.get(),
                    "responseLength", fullResponse.length(), "mode", session.writingMode ? "writing" : session.guestMode ? "guest" : "user",
                    "ttsEnabled", session.ttsEnabled));
            if (session.ttsEnabled && !session.ttsFutures.isEmpty()) {
                CompletableFuture.allOf(session.ttsFutures.toArray(new CompletableFuture[0]))
                        .orTimeout(Math.max(30_000L, aiChatProperties.getSseTimeout()), TimeUnit.MILLISECONDS)
                        .whenComplete((ignored, error) -> {
                            try {
                                session.send("audio-complete", SseEmitterHelper.eventPayload("conversationId", session.conversationId.get(),
                                        "timedOut", error instanceof TimeoutException, "segments", session.seq.get()));
                            } catch (Exception sendError) {
                                log.debug("发送 audio-complete 失败: {}", sendError.getMessage());
                            } finally {
                                session.closeChannel();
                                session.emitter.complete();
                            }
                        });
            } else {
                session.closeChannel();
                session.emitter.complete();
            }
        } catch (Exception error) {
            log.warn("AI 流式收尾失败: model={}, conversationId={}", session.modelName, session.conversationId.get(), error);
            session.savePartialOnce();
            session.finishMetrics("error");
            SseEmitterHelper.safeSendError(session.emitter, session.conversationId.get(), toUserFriendlyError(error));
            session.closeChannel();
            // 错误已经通过SSE协议发送，正常关闭以免异步异常分派再写JSON破坏流。
            session.emitter.complete();
        }
    }

    private void sendSegments(StreamSession session, List<String> segments) {
        for (String segment : segments) {
            if (session.closed.get()) return;
            int number = session.seq.incrementAndGet();
            sendAvatarCue(session.emitter, number, session.conversationId.get(), segment);
            if (session.ttsEnabled) {
                enqueueTtsTask(session.emitter, session.ttsExecutor.get(), session.ttsFutures,
                        number, session.conversationId.get(), segment);
            }
        }
    }

    /** 请求作用域的终态、订阅与资源；句柄晚到时也必须观察已经发生的取消。 */
    private final class StreamSession {
        final SseEmitter emitter = new SseEmitter(Math.max(1, aiChatProperties.getSseTimeout()) + 1000);
        final String userId;
        final boolean guestMode;
        final boolean writingMode;
        final String modelName;
        final long startedNanos = System.nanoTime();
        final AtomicReference<Long> conversationId;
        final AtomicBoolean modelEnded = new AtomicBoolean();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicBoolean metricsFinished = new AtomicBoolean();
        final AtomicBoolean assistantSaved = new AtomicBoolean();
        final AtomicBoolean firstToken = new AtomicBoolean();
        final AtomicReference<Disposable> subscription = new AtomicReference<>();
        final AtomicReference<Future<?>> preparation = new AtomicReference<>();
        final AtomicReference<Future<?>> heartbeat = new AtomicReference<>();
        final AtomicReference<Future<?>> deadline = new AtomicReference<>();
        final AtomicReference<ExecutorService> ttsExecutor = new AtomicReference<>();
        final AiMetrics.UsageTracker usage = new AiMetrics.UsageTracker();
        final FieldUpdateCollector collector = new FieldUpdateCollector();
        final FieldUpdateParser parser = new FieldUpdateParser();
        final StringBuilder response = new StringBuilder();
        final StringBuilder textBuffer = new StringBuilder();
        final AtomicInteger seq = new AtomicInteger();
        final List<CompletableFuture<Void>> ttsFutures = Collections.synchronizedList(new ArrayList<>());
        volatile boolean ttsEnabled;
        boolean contentRequested;
        String originalDraftContent;
        List<String> allowedWritingFields = List.of();
        volatile Thread preparationThread;
        boolean userMessageSaved;

        StreamSession(Long user, String model, boolean writing, Long conversation) {
            userId = user == null ? null : user.toString();
            guestMode = user == null;
            writingMode = writing;
            modelName = model;
            conversationId = new AtomicReference<>(conversation);
            aiMetrics.recordStreamStarted(modelName, mode());
        }
        String mode() { return writingMode ? "writing" : "chat"; }
        boolean allowsBodyPreview() {
            return allowedWritingFields.isEmpty() || (allowedWritingFields.contains("content")
                    && !allowedWritingFields.contains("check"));
        }
        boolean isRunning() { return !modelEnded.get() && !closed.get(); }
        long elapsedMillis() { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos); }
        synchronized void append(String text) { response.append(text); }
        synchronized String responseText() { return response.toString(); }
        void send(String event, Map<String, Object> payload) throws java.io.IOException {
            if (closed.get()) throw new java.io.IOException("SSE 连接已关闭");
            SseEmitterHelper.sendSseEvent(emitter, event, payload);
        }
        void setSubscription(Disposable disposable) {
            subscription.set(disposable);
            if (cancelled.get() || closed.get()) disposable.dispose();
        }
        void setFuture(Future<?> future) {
            preparation.set(future);
            if (cancelled.get()) future.cancel(true);
        }
        void setHeartbeat(Future<?> task) {
            heartbeat.set(task);
            if (closed.get()) task.cancel(true);
        }
        void setDeadline(Future<?> task) {
            deadline.set(task);
            if (closed.get()) task.cancel(true);
        }
        void setTtsExecutor(ExecutorService executor) {
            ttsExecutor.set(executor);
            if (closed.get()) SseEmitterHelper.shutdown(ttsExecutor.getAndSet(null), true);
        }
        void finishMetrics(String outcome) {
            if (metricsFinished.compareAndSet(false, true)) {
                aiMetrics.recordStreamFinished(modelName, mode(), outcome, elapsedMillis(), usage);
            }
        }
        synchronized void savePartialOnce() {
            if (guestMode || writingMode || !userMessageSaved || !assistantSaved.compareAndSet(false, true)) return;
            try {
                String partial = responseText();
                memoryService.saveAssistantMessage(userId, conversationId.get(), partial.isBlank() ? null : partial,
                        modelName, MemoryService.MESSAGE_STATUS_ERROR, null);
            } catch (Exception error) {
                log.warn("保存中断的 AI 回复失败: userId={}, conversationId={}", userId, conversationId.get(), error);
            }
        }
        void stop(String outcome, Throwable error, boolean notify) {
            boolean stopped;
            synchronized (this) {
                stopped = modelEnded.compareAndSet(false, true);
            }
            if (stopped) {
                if (error != null && "error".equals(outcome)) {
                    log.warn("AI 流式请求失败: model={}, userId={}, conversationId={}",
                            modelName, userId, conversationId.get(), error);
                }
                cancelled.set(true);
                Disposable disposable = subscription.get();
                if (disposable != null) disposable.dispose();
                Future<?> future = preparation.get();
                if (future != null) future.cancel(Thread.currentThread() != preparationThread);
                savePartialOnce();
                finishMetrics(outcome);
                if (notify && !closed.get()) {
                    SseEmitterHelper.safeSendError(emitter, conversationId.get(), "timeout".equals(outcome)
                            ? timeoutNotice() : toUserFriendlyError(error));
                }
            }
            closeChannel();
            // 终态到达后才关闭，timeout 提示先发送。
            if (notify || stopped) {
                emitter.complete();
            }
        }
        void closeChannel() {
            if (!closed.compareAndSet(false, true)) return;
            activeSessions.remove(this);
            Future<?> task = heartbeat.getAndSet(null);
            if (task != null) task.cancel(true);
            task = deadline.getAndSet(null);
            if (task != null) task.cancel(true);
            SseEmitterHelper.shutdown(ttsExecutor.getAndSet(null), true);
            streamPermits.release();
        }
    }

    // ==================== TTS / AvatarCue ====================
    /**
     * 为一段文本生成 Live2D 表情动作提示并通过 SSE 推给前端,失败仅记 debug 日志不打断主流程。
     */
    private void sendAvatarCue(SseEmitter emitter, int seq, Long conversationId, String text) {
        try {
            AvatarCuePayload cue = avatarCueService.fromText(seq, conversationId, text);
            SseEmitterHelper.sendSseEvent(emitter, "avatar-cue", SseEmitterHelper.eventPayload(
                    "seq", seq,
                    "conversationId", conversationId,
                    "expression", cue.getExpression(),
                    "motion", cue.getMotion(),
                    "intensity", cue.getIntensity(),
                    "durationMs", cue.getDurationMs(),
                    "text", cue.getText()));
        } catch (Exception e) {
            log.debug("发送avatar-cue失败: {}", e.getMessage());
        }
    }

    /** 匹配 [标题](/post/ID) 格式的文章引用链接 */
    private static final Pattern POST_LINK_PATTERN =
            Pattern.compile("\\[([^\\]]+)\\]\\(/post/(\\d+)\\)");

    /** 卡片引用必须重新查询博客事实；模型编造的 ID 和标题不进入推荐结果。 */
    private List<PostSummaryDTO> extractArticleResults(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        Set<Long> ids = new LinkedHashSet<>();
        Matcher matcher = POST_LINK_PATTERN.matcher(text);
        while (matcher.find() && ids.size() < aiChatProperties.getAgent().getMaxArticleResults()) {
            try { ids.add(Long.parseLong(matcher.group(2))); }
            catch (NumberFormatException ignored) { }
        }
        List<PostSummaryDTO> result = new ArrayList<>();
        for (Long id : ids) {
            PostDetailDTO detail = blogApiClient.getPostDetail(id);
            if (detail == null || detail.getId() == null || !id.equals(detail.getId())
                    || detail.getTitle() == null || detail.getTitle().isBlank()) continue;
            PostSummaryDTO summary = new PostSummaryDTO();
            summary.setId(id);
            summary.setTitle(detail.getTitle());
            summary.setSummary(detail.getSummary());
            summary.setCategoryName(detail.getCategoryName());
            result.add(summary);
        }
        return result;
    }

    /**
     * 提交一段文本到 TTS 线程池,合成完成后通过 audio 事件带 seq/audioUrl 推给前端。
     *
     * seq 与 avatar-cue、data 事件共享,前端按序号对齐播放。
     * 空文本或不可播报文本(纯符号等)直接跳过;合成失败或返回空 URL 时发 audio-skip 事件,不影响主流程。
     */
    private void enqueueTtsTask(
            SseEmitter emitter, ExecutorService executor,
            List<CompletableFuture<Void>> futures,
            int seq, Long conversationId, String text
    ) {
        String segment = text == null ? "" : text.trim();
        if (segment.isEmpty() || !ttsSegmenter.containsSpeakableText(segment)) return;

        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                String audioUrl = ttsSpeechService.inferSingleAudioUrl(segment);
                if (audioUrl == null || audioUrl.isBlank()) {
                    SseEmitterHelper.sendSseEvent(emitter, "audio-skip", SseEmitterHelper.eventPayload(
                            "seq", seq, "text", segment, "reason", "empty-audio-url", "conversationId", conversationId));
                    return;
                }
                SseEmitterHelper.sendSseEvent(emitter, "audio", SseEmitterHelper.eventPayload(
                        "seq", seq, "text", segment, "audioUrl", audioUrl, "conversationId", conversationId));
            } catch (Exception e) {
                try {
                    SseEmitterHelper.sendSseEvent(emitter, "audio-skip", SseEmitterHelper.eventPayload(
                            "seq", seq, "text", segment, "reason", e.getClass().getSimpleName(), "conversationId", conversationId));
                } catch (Exception ignore) {
                }
            }
        }, executor);
        futures.add(task);
    }

    private static final Set<String> ARTICLE_TAGS = Set.of("article", "section", "div", "h1", "h2", "h3", "h4", "h5", "h6", "p", "pre", "code", "ul", "ol", "li", "blockquote", "strong", "em", "b", "i", "s", "del", "a", "img", "br", "hr", "table", "thead", "tbody", "tfoot", "tr", "th", "td", "span", "figure", "figcaption");
    private static final Set<String> VOID_TAGS = Set.of("img", "br", "hr");
    private static final Set<String> BLOCK_TAGS = Set.of("article", "section", "div", "h1", "h2", "h3", "h4", "h5", "h6", "p", "pre", "ul", "ol", "blockquote", "table", "figure");
    private static final Set<String> EXISTING_MEDIA_TAGS = Set.of("audio", "video", "iframe", "svg", "math");
    private static final Pattern HTML_TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^<>\"']|\"[^\"]*\"|'[^']*')*)>");
    private static final Pattern UNSAFE_ATTRIBUTE = Pattern.compile("(?i)\\s(?:on[a-z]+|srcdoc)\\s*=|(?:javascript|vbscript|data)\\s*:");

    /** 只接受完整合法的文章 HTML 片段，禁止从聊天/半截正文中猜测一个可回写区间。 */
    private String completeArticleHtml(String text) {
        return completeArticleHtml(text, null);
    }

    private String completeArticleHtml(String text, String originalContent) {
        if (text == null) return null;
        String html = text.trim();
        if (!html.startsWith("<") || !html.endsWith(">")) return null;
        // 原稿完整媒体节点只供原样保留：验证时遮罩其内部结构，返回时仍使用完整原输出。
        // 前端会在惰性 DOM 中净化保留节点；新媒体或被改动的子资源不会获得例外。
        String validationHtml = maskExistingMedia(html, originalContent);
        Matcher matcher = HTML_TAG.matcher(validationHtml);
        Deque<String> stack = new ArrayDeque<>();
        int previous = 0;
        boolean first = true;
        while (matcher.find()) {
            String between = validationHtml.substring(previous, matcher.start());
            if (between.indexOf('<') >= 0 || (stack.isEmpty() && !between.isBlank())) return null;
            String tag = matcher.group(2).toLowerCase(Locale.ROOT);
            String attributes = matcher.group(3);
            if (!ARTICLE_TAGS.contains(tag) || UNSAFE_ATTRIBUTE.matcher(attributes).find()) return null;
            boolean closing = !matcher.group(1).isEmpty();
            if (first && (closing || !BLOCK_TAGS.contains(tag))) return null;
            first = false;
            if (closing) {
                if (!attributes.isBlank() || stack.isEmpty() || !stack.pop().equals(tag)) return null;
            } else if (!VOID_TAGS.contains(tag)) {
                if (!attributes.endsWith("/")) stack.push(tag);
            }
            previous = matcher.end();
        }
        return !first && stack.isEmpty() && validationHtml.substring(previous).isBlank() ? html : null;
    }

    /** 复用标签扫描器提取五类既有媒体的完整根节点，不解析或信任生成内容中的未知节点。 */
    private String maskExistingMedia(String html, String originalContent) {
        if (originalContent == null || originalContent.isBlank()) return html;
        Matcher matcher = HTML_TAG.matcher(originalContent);
        String mediaTag = null;
        int start = 0;
        int depth = 0;
        List<String> nodes = new ArrayList<>();
        while (matcher.find()) {
            String tag = matcher.group(2).toLowerCase(Locale.ROOT);
            boolean closing = !matcher.group(1).isEmpty();
            boolean selfClosing = matcher.group(3).trim().endsWith("/");
            if (mediaTag == null) {
                if (closing || !EXISTING_MEDIA_TAGS.contains(tag)) continue;
                start = matcher.start();
                if (selfClosing) {
                    nodes.add(originalContent.substring(start, matcher.end()));
                } else {
                    mediaTag = tag;
                    depth = 1;
                }
            } else if (mediaTag.equals(tag)) {
                if (closing) depth--;
                else if (!selfClosing) depth++;
                if (depth == 0) {
                    nodes.add(originalContent.substring(start, matcher.end()));
                    mediaTag = null;
                }
            }
        }
        for (String node : nodes) html = html.replace(node, "<div></div>");
        return html;
    }

    /**
     * FieldUpdatePayload 转为 SSE 事件 payload Map（只含非 null 字段，对齐前端 FieldUpdatePayload）。
     *
     * 额外附带 {@code fields}：本次真正写入的字段名列表。前端据此显示"已写入：标题、标签"，
     * 而不是自己猜步骤 —— 过去前端把固定 5 步计划当进度展示，与后端实际行为无关。
     */
    private Map<String, Object> toPayloadMap(FieldUpdatePayload fu) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<String> writtenFields = new ArrayList<>();

        putIfPresent(map, writtenFields, "title", fu.getTitle());
        putIfPresent(map, writtenFields, "summary", fu.getSummary());
        putIfPresent(map, writtenFields, "categoryId", fu.getCategoryId());
        putIfPresent(map, writtenFields, "categoryName", fu.getCategoryName());
        putIfPresent(map, writtenFields, "tagIds", fu.getTagIds());
        putIfPresent(map, writtenFields, "tagNames", fu.getTagNames());
        putIfPresent(map, writtenFields, "suggestedCategoryName", fu.getSuggestedCategoryName());
        putIfPresent(map, writtenFields, "suggestedTagNames", fu.getSuggestedTagNames());

        if (!writtenFields.isEmpty()) {
            map.put("fields", writtenFields);
        }
        return map;
    }

    /** 非 null 才写入 payload，同时登记字段名（用于 fields 列表） */
    private void putIfPresent(Map<String, Object> map, List<String> writtenFields, String key, Object value) {
        if (value != null) {
            map.put(key, value);
            writtenFields.add(key);
        }
    }

    /**
     * 把底层技术性错误文案转成用户可读的中文提示（发给前端的 error 事件用）。
     * 原始错误仍完整记录在服务端日志里，这里只做展示层映射，不丢排查信息。
     */
    /** SSE 超时提示：说明等了多久、以及可以怎么做（具体的秒数比"连接中断"有用得多） */
    private String timeoutNotice() {
        long seconds = aiChatProperties.getSseTimeout() / 1000;
        return "AI 响应超时：已等待 " + seconds + " 秒仍未完成。内容较多时模型思考时间会变长，"
                + "请稍后重试，或把内容分段后再试";
    }

    /**
     * 异常 → 用户可读文案（带类型判断的版本）。
     *
     * {@link AIServiceException} 的文案是本项目自己拼的、本就面向用户
     * （例如"输入内容过长：本次请求必需内容约 N token…"），必须原样透出；
     * 只有底层技术异常（okhttp/上游报文）才走关键词映射，避免把技术细节丢给用户。
     */
    private String toUserFriendlyError(Throwable error) {
        if (error instanceof AIServiceException) {
            String message = error.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return toUserFriendlyError(error == null ? null : error.getMessage());
    }

    private String toUserFriendlyError(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "AI 服务暂时不可用，请稍后重试";
        }
        String msg = rawMessage.toLowerCase();
        if (msg.contains("timeout") || msg.contains("超时") || msg.contains("timed out")) {
            return "AI 响应超时：内容较多时模型思考时间会变长，请稍后重试，或将内容分段后分次处理";
        }
        // 上游中英文都可能出现：英文 maximum context length / 中文 上下文长度、tokens 超限
        if (msg.contains("context") || msg.contains("token") || msg.contains("length") || msg.contains("maximum")
                || msg.contains("上下文") || msg.contains("长度") || msg.contains("超出")) {
            return "输入内容过长，已超出模型单次处理的上下文范围，请精简后再试，或在管理端为该模型调大上下文窗口";
        }
        if (msg.contains("busy") || msg.contains("429") || msg.contains("rate") || msg.contains("quota")
                || msg.contains("繁忙") || msg.contains("限流") || msg.contains("频率")) {
            return "AI 服务当前繁忙，请稍后重试";
        }
        return "AI 服务暂时未能完成回复，请稍后重试";
    }

}
