package chat.liuxin.ai.service;

import chat.liuxin.ai.common.client.TtsClient;
import chat.liuxin.ai.common.tts.AvatarCueService;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import chat.liuxin.ai.dto.AvatarCuePayload;
import chat.liuxin.ai.dto.FieldUpdatePayload;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.PostSummaryDTO;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式聊天服务。
 *
 * 从 AiChatServiceImpl 中抽取，专门处理 SSE 流式响应，包含：
 * - 看板聊天流式（processStreamChat）
 * - 写作助手流式（processWritingStream）
 * - TTS 语音合成编排
 * - Live2D 表情提示（AvatarCue）
 * - SSE 心跳保活
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingChatService {

    // ========== 流式内容回写阈值 ==========
    /** HTML 片段最小长度，低于此不作为正文回写 */
    private static final int MIN_HTML_LENGTH = 200;
    /** 文章正文最小长度，超过才视为完整文章 */
    private static final int MIN_ARTICLE_LENGTH = 400;
    /** HTML 片段最小长度（extractHtmlBody 内分段判断） */
    private static final int MIN_HTML_PART_LENGTH = 100;
    /** content-update 事件触发的文本增量阈值 */
    private static final int CONTENT_UPDATE_LENGTH_THRESHOLD = 300;
    /** content-update 事件触发的最小时间间隔（毫秒） */
    private static final long CONTENT_UPDATE_INTERVAL_MS = 800;
    /** 心跳首次延迟（秒） */
    private static final long HEARTBEAT_INITIAL_DELAY_SEC = 15;
    /** 心跳发送间隔（秒） */
    private static final long HEARTBEAT_INTERVAL_SEC = 15;

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    private final ChatServiceHelper chatServiceHelper;
    private final TtsClient ttsClient;
    private final TtsSegmenter ttsSegmenter;
    private final AvatarCueService avatarCueService;
    private final AiChatProperties aiChatProperties;

    /** 流式任务线程池大小：个人博客并发有限，固定 16 足够，避免 commonPool 饥饿 */
    private static final int STREAM_POOL_SIZE = 16;

    /** SSE 流式任务专用线程池，避免长阻塞任务占用 ForkJoinPool.commonPool 影响其他并行计算 */
    private ExecutorService streamExecutor;

    // ==================== 线程池生命周期 ====================

    /** 初始化流式线程池 */
    @PostConstruct
    void initStreamExecutor() {
        streamExecutor = Executors.newFixedThreadPool(STREAM_POOL_SIZE);
    }

    /** 优雅关闭流式线程池 */
    @PreDestroy
    void shutdownStreamExecutor() {
        streamExecutor.shutdown();
        try {
            if (!streamExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                streamExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            streamExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 在流式线程池上执行任务，避免占用 ForkJoinPool.commonPool */
    private void runOnStreamPool(Runnable task) {
        CompletableFuture.runAsync(task, streamExecutor);
    }

    // ==================== 公开接口 ====================

    /**
     * 看板娘流式聊天入口。
     *
     * 立刻返回 {@link SseEmitter},真正逻辑跑在 {@link CompletableFuture#runAsync} 上,避免占用 Servlet 线程。
     *
     * 生命周期:
     * - 先注册 onCompletion / onTimeout,确保心跳线程和 TTS 线程池一定被关闭。
     * - 异步任务里:登录且无会话时先建会话 → 落库用户消息 → 发送 start 事件 →
     *   起 15s 心跳定时任务 → 订阅底层 flux 并处理数据/TTS/AvatarCue。
     * - 完成回调里落库完整 assistant 消息(status=1);错误回调里落库 partial 文本(status=3)
     *   并写一条错误占位,再向前端发 error 事件。
     */
    public SseEmitter processStreamChat(ChatRequest request, Long userId, String modelName,
                                        AiModelPolicy.ModelParameters params, String role) {
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        Long conversationId = guestMode ? null : request.getConversationId();
        String input = request.getMessage();

        SseEmitter emitter = new SseEmitter(aiChatProperties.getSseTimeout());
        AtomicReference<ExecutorService> ttsExecutorRef = new AtomicReference<>();
        AtomicReference<ScheduledExecutorService> heartbeatRef = new AtomicReference<>();
        AtomicBoolean emitterClosed = new AtomicBoolean(false);

        emitter.onCompletion(() -> {
            emitterClosed.set(true);
            SseEmitterHelper.shutdown(heartbeatRef.getAndSet(null), true);
            SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
        });
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            SseEmitterHelper.shutdown(heartbeatRef.getAndSet(null), true);
            SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
            emitter.complete();
        });

        runOnStreamPool(() -> {
            Long convId = conversationId;
            if (!guestMode && convId == null) {
                convId = memoryService.createConversation(userIdStr, chatServiceHelper.generateTitle(input));
            }
            final Long finalConvId = convId;

            try {
                List<Message> messages = chatServiceHelper.prepareMessages(request, userIdStr, finalConvId, guestMode, false);
                if (!guestMode && finalConvId != null) {
                    memoryService.saveUserMessage(userIdStr, finalConvId, input, modelName, null);
                }

                SseEmitterHelper.sendSseEvent(emitter, "start", SseEmitterHelper.eventPayload(
                        "conversationId", finalConvId, "model", modelName, "mode", guestMode ? "guest" : "user"));

                ScheduledExecutorService hb = Executors.newSingleThreadScheduledExecutor();
                heartbeatRef.set(hb);
                hb.scheduleAtFixedRate(() -> {
                    if (emitterClosed.get()) return;
                    try {
                        SseEmitterHelper.sendSseEvent(emitter, "heartbeat", SseEmitterHelper.eventPayload(
                                "conversationId", finalConvId, "timestamp", System.currentTimeMillis()));
                    } catch (Exception e) {
                        log.debug("心跳发送失败: {}", e.getMessage());
                    }
                }, HEARTBEAT_INITIAL_DELAY_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);

                Flux<String> flux = siliconFlowChatClient.streamChat(messages, modelName, params.temperature(), params.maxTokens(), role);
                boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());

                subscribeStream(emitter, flux, finalConvId, ttsEnabled, emitterClosed, ttsExecutorRef,
                        guestMode, userIdStr, modelName, false, null, null,
                        fullResponse -> {
                            if (!guestMode && finalConvId != null) {
                                memoryService.saveAssistantMessage(userIdStr, finalConvId, fullResponse, modelName, MemoryService.MESSAGE_STATUS_NORMAL, null);
                            }
                        },
                        (partial, errorMsg) -> {
                            if (!guestMode && finalConvId != null && partial != null && !partial.isBlank()) {
                                memoryService.saveAssistantMessage(userIdStr, finalConvId, partial, modelName, MemoryService.MESSAGE_STATUS_ERROR, null);
                            }
                            chatServiceHelper.saveErrorIfNeeded(guestMode, userIdStr, finalConvId, modelName);
                        });

            } catch (Exception e) {
                log.error("流式聊天处理失败，用户ID: {}, 会话ID: {}", userIdStr, finalConvId, e);
                chatServiceHelper.saveErrorIfNeeded(guestMode, userIdStr, finalConvId, modelName);
                SseEmitterHelper.safeSendError(emitter, finalConvId, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 写作助手流式入口。
     *
     * 与看板娘流式的区别:走 WRITING 模式(注册 WritingTools)、不落库。
     * 心跳线程与看板娘一致（CDN 空闲超时会掐断长流，写作长文生成停顿久，
     * 曾因无心跳导致 ERR_HTTP2_PROTOCOL_ERROR；heartbeat 事件前端已兼容）。
     * 其他 SSE 生命周期、TTS 编排、AvatarCue 逻辑与 {@link #processStreamChat} 共用。
     */
    public SseEmitter processWritingStream(ChatRequest request, Long userId, String modelName,
                                           AiModelPolicy.ModelParameters params, String role) {
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        Long conversationId = guestMode ? null : request.getConversationId();

        SseEmitter emitter = new SseEmitter(aiChatProperties.getSseTimeout());
        AtomicReference<ExecutorService> ttsExecutorRef = new AtomicReference<>();
        AtomicReference<ScheduledExecutorService> heartbeatRef = new AtomicReference<>();
        AtomicBoolean emitterClosed = new AtomicBoolean(false);

        emitter.onCompletion(() -> {
            emitterClosed.set(true);
            SseEmitterHelper.shutdown(heartbeatRef.getAndSet(null), true);
            SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
        });
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            SseEmitterHelper.shutdown(heartbeatRef.getAndSet(null), true);
            SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
            emitter.complete();
        });

        runOnStreamPool(() -> {
            try {
                List<Message> messages = chatServiceHelper.prepareMessages(request, userIdStr, conversationId, guestMode, true);

                SseEmitterHelper.sendSseEvent(emitter, "start", SseEmitterHelper.eventPayload(
                        "conversationId", conversationId, "model", modelName, "mode", "writing"));

                ScheduledExecutorService hb = Executors.newSingleThreadScheduledExecutor();
                heartbeatRef.set(hb);
                hb.scheduleAtFixedRate(() -> {
                    if (emitterClosed.get()) return;
                    try {
                        SseEmitterHelper.sendSseEvent(emitter, "heartbeat", SseEmitterHelper.eventPayload(
                                "conversationId", conversationId, "timestamp", System.currentTimeMillis()));
                    } catch (Exception e) {
                        log.debug("心跳发送失败: {}", e.getMessage());
                    }
                }, HEARTBEAT_INITIAL_DELAY_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);

                FieldUpdateCollector collector = new FieldUpdateCollector();
                Map<String, Object> toolContext = new HashMap<>();
                toolContext.put(FieldUpdateCollector.CONTEXT_KEY, collector);


                // 工具事件回调：工具 start/success/error 实时转成 SSE tool-start/tool-result 事件推给前端
                WritingToolEventSink toolEventSink = new WritingToolEventSink((eventName, payload) -> {
                    try {
                        SseEmitterHelper.sendSseEvent(emitter, eventName, payload);
                    } catch (Exception e) {
                        log.debug("发送工具事件SSE失败: {}", e.getMessage());
                    }
                });
                toolContext.put(WritingToolEventSink.CONTEXT_KEY, toolEventSink);
                Flux<String> flux = siliconFlowChatClient.streamChat(messages, modelName, params.temperature(), params.maxTokens(), SiliconFlowChatClient.ChatMode.WRITING, role, toolContext);
                boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());

                subscribeStream(emitter, flux, conversationId, ttsEnabled, emitterClosed, ttsExecutorRef,
                        guestMode, userIdStr, modelName, true, new FieldUpdateParser(), collector,
                        fullResponse -> {}, (partial, errorMsg) -> {});

            } catch (Exception e) {
                log.error("写作助手流式处理失败，用户ID: {}, 会话ID: {}", userIdStr, conversationId, e);
                SseEmitterHelper.safeSendError(emitter, conversationId, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ==================== 流式订阅核心（TTS + AvatarCue + SSE） ====================

    /**
     * 订阅底层 flux 并统一处理三种事件:数据分片、错误、完成。
     *
     * onNext: 把 chunk 累加到全量文本和分段缓冲区,由 {@link TtsSegmenter} 切出可播报段落;
     * 每个段落分配自增 seq,先发 avatar-cue 事件保证表情提示时序,再按需异步跑 TTS;
     * 原始 chunk 通过 data 事件透传给前端。
     *
     * onError: 触发 onError 回调(用于持久化 partial 文本)、发 error 事件、强制关线程池、completeWithError。
     *
     * onComplete: 触发 onComplete 回调(用于落库完整 assistant 消息)、flush 剩余文本、
     * 从全量文本抽取 [标题](/post/ID) 生成 article-results、发 complete 事件;
     * 若开启 TTS,再异步等所有 TTS 任务完成后发 audio-complete,最后 emitter.complete()。
     * TTS 等待有超时保护(至少 30s 或配置的 sseTimeout),超时也照常收尾。
     */
    private void subscribeStream(
            SseEmitter emitter,
            Flux<String> flux,
            Long conversationId,
            boolean ttsEnabled,
            AtomicBoolean emitterClosed,
            AtomicReference<ExecutorService> ttsExecutorRef,
            boolean guestMode,
            String userIdStr,
            String modelName,
            boolean writingMode,
            FieldUpdateParser parser,
            FieldUpdateCollector collector,
            java.util.function.Consumer<String> onComplete,
            java.util.function.BiConsumer<String, String> onError
    ) {
        AtomicReference<StringBuilder> fullResponseRef = new AtomicReference<>(new StringBuilder());
        StringBuilder textBuffer = new StringBuilder();
        AtomicInteger seq = new AtomicInteger(0);
        AtomicBoolean fieldUpdateSent = new AtomicBoolean(false);
        AtomicInteger lastContentUpdateLength = new AtomicInteger(0);
        AtomicLong lastContentUpdateTime = new AtomicLong(System.currentTimeMillis());

        // 写作模式：collector 实时监听，AI 每次调 applyArticleUpdate 立即发 SSE field-update 事件
        // 不等 onComplete，避免用户看到长时等待；collector 由 processWritingStream 注入
        if (writingMode && collector != null) {
            collector.addListener(payload -> {
                try {
                    SseEmitterHelper.sendSseEvent(emitter, "field-update", toPayloadMap(payload));
                    fieldUpdateSent.set(true);
                } catch (Exception e) {
                    log.warn("实时发送field-update事件失败: {}", e.getMessage());
                }
            });
        }

        int poolSize = Math.max(1, aiChatProperties.getTtsStreamConcurrency());
        ExecutorService ttsExecutor = Executors.newFixedThreadPool(poolSize);
        ttsExecutorRef.set(ttsExecutor);
        List<CompletableFuture<Void>> ttsFutures = Collections.synchronizedList(new ArrayList<>());

        flux.subscribe(
                // ---- onNext ----
                chunk -> {
                    try {
                        if (writingMode && parser != null) {
                            handleWritingChunk(emitter, parser, chunk, fullResponseRef, textBuffer, seq,
                                    ttsEnabled, ttsExecutor, ttsFutures, conversationId, fieldUpdateSent,
                                    lastContentUpdateLength, lastContentUpdateTime);
                            return;
                        }
                        fullResponseRef.get().append(chunk);
                        textBuffer.append(chunk);

                        List<String> segments = ttsSegmenter.extractSegments(textBuffer, seq.get() > 0);
                        for (String seg : segments) {
                            int currentSeq = seq.incrementAndGet();
                            sendAvatarCue(emitter, currentSeq, conversationId, seg);
                            if (ttsEnabled) {
                                enqueueTtsTask(emitter, ttsExecutor, ttsFutures, currentSeq, conversationId, seg);
                            }
                        }

                        SseEmitterHelper.sendSseEvent(emitter, "data", SseEmitterHelper.eventPayload(
                                "content", chunk, "conversationId", conversationId));
                    } catch (java.io.IOException e) {
                        log.error("发送SSE事件失败", e);
                        emitter.completeWithError(e);
                    }
                },
                // ---- onError ----
                error -> {
                    log.error("流式响应错误，用户ID: {}, 会话ID: {}", userIdStr, conversationId, error);
                    String errorMsg = error != null ? error.getMessage() : "未知错误";
                    onError.accept(fullResponseRef.get().toString(), errorMsg);
                    // 发给前端的文案做友好映射，避免把 okhttp 堆栈术语直接丢给用户
                    SseEmitterHelper.safeSendError(emitter, conversationId, toUserFriendlyError(errorMsg));
                    emitterClosed.set(true);
                    SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
                    emitter.completeWithError(error != null ? error : new RuntimeException("流式响应发生未知错误"));
                },
                // ---- onComplete ----
                () -> {
                    try {
                        if (writingMode && parser != null) {
                            String rest = parser.flush();
                            if (rest != null && !rest.isEmpty()) {
                                fullResponseRef.get().append(rest);
                                textBuffer.append(rest);
                                SseEmitterHelper.sendSseEvent(emitter, "data", SseEmitterHelper.eventPayload(
                                        "content", rest, "conversationId", conversationId));
                            }
                        }
                        String fullResponse = fullResponseRef.get().toString();
                        onComplete.accept(fullResponse);


                        // 正文回写：AI 自然输出的 HTML 文本（fullResponse）作为 contentHtml 写入编辑器。
                        // AI 通过 applyArticleUpdate 工具实时回写了 title/summary/category/tagIds，
                        // 正文因为是流文本不通过工具传（避免 tool arguments 长文本阻塞流式体验）。
                        // 若整轮连工具都没调过且文本像文章，也走这条路径作为兜底。
                        if (writingMode && looksLikeArticleContent(fullResponse)) {
                            Map<String, Object> contentUpdate = new LinkedHashMap<>();
                            String finalHtml = extractHtmlBody(fullResponse);
                            if (finalHtml != null && finalHtml.length() >= MIN_HTML_LENGTH
                                    && (finalHtml.contains("<p") || finalHtml.contains("<h") || finalHtml.contains("<pre"))) {
                                contentUpdate.put("contentHtml", finalHtml);
                                SseEmitterHelper.sendSseEvent(emitter, "field-update", contentUpdate);
                            }
                        }

                        // flush 剩余文本
                        List<String> tailSegments = ttsSegmenter.extractSegments(textBuffer, seq.get() > 0);
                        for (String seg : tailSegments) {
                            int currentSeq = seq.incrementAndGet();
                            sendAvatarCue(emitter, currentSeq, conversationId, seg);
                            if (ttsEnabled) {
                                enqueueTtsTask(emitter, ttsExecutor, ttsFutures, currentSeq, conversationId, seg);
                            }
                        }
                        String rest = textBuffer.toString().trim();
                        textBuffer.setLength(0);
                        if (!rest.isEmpty()) {
                            int currentSeq = seq.incrementAndGet();
                            sendAvatarCue(emitter, currentSeq, conversationId, rest);
                            if (ttsEnabled) {
                                enqueueTtsTask(emitter, ttsExecutor, ttsFutures, currentSeq, conversationId, rest);
                            }
                        }

                        SseEmitterHelper.sendSseEvent(emitter, "article-results", SseEmitterHelper.eventPayload(
                                "items", extractArticleResults(fullResponse),
                                "reason", "我找到这些文章，可以直接点开阅读。"));
                        SseEmitterHelper.sendSseEvent(emitter, "complete", SseEmitterHelper.eventPayload(
                                "conversationId", conversationId,
                                "responseLength", fullResponse.length(),
                                "mode", guestMode ? "guest" : "user",
                                "ttsEnabled", ttsEnabled));

                        if (ttsEnabled && !ttsFutures.isEmpty()) {
                            runOnStreamPool(() -> {
                                boolean timedOut = false;
                                try {
                                    CompletableFuture.allOf(ttsFutures.toArray(new CompletableFuture[0]))
                                            .get(Math.max(30_000L, aiChatProperties.getSseTimeout()), TimeUnit.MILLISECONDS);
                                } catch (TimeoutException e) {
                                    timedOut = true;
                                } catch (Exception e) {
                                    log.warn("等待TTS完成异常: {}", e.getMessage());
                                }
                                try {
                                    SseEmitterHelper.sendSseEvent(emitter, "audio-complete", SseEmitterHelper.eventPayload(
                                            "conversationId", conversationId,
                                            "timedOut", timedOut,
                                            "segments", seq.get()));
                                } catch (Exception ignore) {
                                }
                                emitterClosed.set(true);
                                SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), false);
                                emitter.complete();
                            });
                            return;
                        }

                        emitterClosed.set(true);
                        SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("完成流式响应时发生错误", e);
                        emitterClosed.set(true);
                        SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
                        emitter.completeWithError(e);
                    }
                }
        );
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

    /** 从 AI 回复里解析 [标题](/post/ID) 链接，去重后返回最多 8 篇供前端展示推荐卡片 */
    private List<PostSummaryDTO> extractArticleResults(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        Map<Long, String> unique = new LinkedHashMap<>();
        Matcher m = POST_LINK_PATTERN.matcher(text);
        while (m.find()) {
            try {
                Long id = Long.parseLong(m.group(2));
                unique.putIfAbsent(id, m.group(1));
            } catch (NumberFormatException ignore) {
            }
        }
        if (unique.isEmpty()) return Collections.emptyList();
        List<PostSummaryDTO> result = new ArrayList<>();
        for (Map.Entry<Long, String> entry : unique.entrySet()) {
            PostSummaryDTO dto = new PostSummaryDTO();
            dto.setId(entry.getKey());
            dto.setTitle(entry.getValue());
            result.add(dto);
            if (result.size() >= 8) break;
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
                String audioUrl = ttsClient.inferSingleAudioUrl(segment);
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

    /**
     * 写作模式 chunk 处理：用 FieldUpdateParser 解析 chunk，标记外文本走 data/TTS/AvatarCue，
     * 标记内 JSON 解析为 FieldUpdatePayload 后发 field-update 事件。
     */
    private void handleWritingChunk(SseEmitter emitter, FieldUpdateParser parser, String chunk,
                                    AtomicReference<StringBuilder> fullResponseRef, StringBuilder textBuffer,
                                    AtomicInteger seq, boolean ttsEnabled, ExecutorService ttsExecutor,
                                    List<CompletableFuture<Void>> ttsFutures, Long conversationId,
                                    AtomicBoolean fieldUpdateSent,
                                    AtomicInteger lastContentUpdateLength,
                                    AtomicLong lastContentUpdateTime) throws java.io.IOException {
        FieldUpdateParser.ParseResult pr = parser.feed(chunk);
        for (String dataText : pr.dataTexts()) {
            if (dataText.isEmpty()) continue;
            fullResponseRef.get().append(dataText);
            textBuffer.append(dataText);
            List<String> segments = ttsSegmenter.extractSegments(textBuffer, seq.get() > 0);
            for (String seg : segments) {
                int currentSeq = seq.incrementAndGet();
                sendAvatarCue(emitter, currentSeq, conversationId, seg);
                if (ttsEnabled) {
                    enqueueTtsTask(emitter, ttsExecutor, ttsFutures, currentSeq, conversationId, seg);
                }
            }
            SseEmitterHelper.sendSseEvent(emitter, "data", SseEmitterHelper.eventPayload(
                    "content", dataText, "conversationId", conversationId));
        }
        for (FieldUpdatePayload fu : pr.fieldUpdates()) {
            SseEmitterHelper.sendSseEvent(emitter, "field-update", toPayloadMap(fu));
            fieldUpdateSent.set(true);
        }
        // 正文增量流式更新：严格校验后才发送
        String fullText = fullResponseRef.get().toString();
        int currentLength = fullText.length();
        long now = System.currentTimeMillis();
        if (currentLength - lastContentUpdateLength.get() >= CONTENT_UPDATE_LENGTH_THRESHOLD && now - lastContentUpdateTime.get() >= CONTENT_UPDATE_INTERVAL_MS) {
            String htmlBody = extractHtmlBody(fullText);
            boolean hasValidHtml = htmlBody != null && htmlBody.length() >= MIN_HTML_LENGTH
                    && (htmlBody.contains("<p") || htmlBody.contains("<h") || htmlBody.contains("<pre") || htmlBody.contains("<ul"))
                    && htmlBody.contains(">");
            if (hasValidHtml && htmlBody.length() > lastContentUpdateLength.get()) {
                Map<String, Object> contentUpdate = new LinkedHashMap<>();
                contentUpdate.put("contentHtml", htmlBody);
                SseEmitterHelper.sendSseEvent(emitter, "field-update", contentUpdate);
                lastContentUpdateLength.set(currentLength);
                lastContentUpdateTime.set(now);
                fieldUpdateSent.set(true);
            }
        }
    }

/** 判断 AI 全文回复是否看起来像文章内容（用于兜底写入），避免把纯对话废话塞进编辑器。 */
    private boolean looksLikeArticleContent(String text) {
        if (text == null || text.isBlank()) return false;
        String trimmed = text.trim();
        if (trimmed.length() < MIN_HTML_LENGTH) return false;
        // 含 HTML 标签 或 含代码块/分段结构，视作文章内容
        return trimmed.contains("<p") || trimmed.contains("<h") || trimmed.contains("<pre")
                || trimmed.contains("```") || (trimmed.contains("\n\n") && trimmed.length() > MIN_ARTICLE_LENGTH);
    }

    /**
     * 提取AI回复中的HTML正文部分，去掉前后自然语言说明。
     * 例如："好的，文章如下：<p>xxx</p> 请查收" → "<p>xxx</p>"
     */
    private String extractHtmlBody(String text) {
        if (text == null || text.isBlank()) return text;
        String trimmed = text.trim();
        int firstLt = trimmed.indexOf('<');           // 第一个 '<'（HTML 起始）
        int lastGt = trimmed.lastIndexOf('>');        // 最后一个 '>'（HTML 结束）
        int lastLtAfterGt = trimmed.lastIndexOf('<'); // 最后一个 '<'（可能落在 lastGt 之后，表示尾部有未闭合标签）
        // 若最后一个 '<' 在最后一个 '>' 之后，说明尾部有杂散 '<'，回退到它之前最近的 '>'
        if (lastLtAfterGt > lastGt) {
            lastGt = trimmed.lastIndexOf('>', lastLtAfterGt - 1);
        }
        if (firstLt != -1 && lastGt != -1 && lastGt > firstLt) {
            String htmlPart = trimmed.substring(firstLt, lastGt + 1).trim();
            if (htmlPart.length() >= MIN_HTML_PART_LENGTH) {
                return htmlPart;
            }
        }
        return trimmed;
    }

    /** FieldUpdatePayload 转为 SSE 事件 payload Map（只含非 null 字段，对齐前端 FieldUpdatePayload）。 */
    private Map<String, Object> toPayloadMap(FieldUpdatePayload fu) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (fu.getTitle() != null) map.put("title", fu.getTitle());
        if (fu.getSummary() != null) map.put("summary", fu.getSummary());
        if (fu.getContentHtml() != null) map.put("contentHtml", fu.getContentHtml());
        if (fu.getCategoryId() != null) map.put("categoryId", fu.getCategoryId());
        if (fu.getCategoryName() != null) map.put("categoryName", fu.getCategoryName());
        if (fu.getTagIds() != null) map.put("tagIds", fu.getTagIds());
        if (fu.getTagNames() != null) map.put("tagNames", fu.getTagNames());
        if (fu.getSuggestedCategoryName() != null) map.put("suggestedCategoryName", fu.getSuggestedCategoryName());
        if (fu.getSuggestedTagNames() != null) map.put("suggestedTagNames", fu.getSuggestedTagNames());
        return map;
    }

    /**
     * 把底层技术性错误文案转成用户可读的中文提示（发给前端的 error 事件用）。
     * 原始错误仍完整记录在服务端日志里，这里只做展示层映射，不丢排查信息。
     */
    private String toUserFriendlyError(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "AI 服务暂时不可用，请稍后重试";
        }
        String msg = rawMessage.toLowerCase();
        if (msg.contains("timeout")) {
            return "AI 响应超时：内容较多时模型思考时间会变长，请稍后重试，或将内容分段后分次处理";
        }
        if (msg.contains("context") || msg.contains("token") || msg.contains("length") || msg.contains("maximum")) {
            return "输入内容过长，已超出模型单次处理的上下文范围，请精简后再试";
        }
        if (msg.contains("busy") || msg.contains("429") || msg.contains("rate") || msg.contains("quota")) {
            return "AI 服务当前繁忙，请稍后重试";
        }
        return "AI 生成失败：" + rawMessage;
    }

}




