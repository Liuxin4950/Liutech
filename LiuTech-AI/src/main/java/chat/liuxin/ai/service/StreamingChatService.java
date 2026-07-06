package chat.liuxin.ai.service;

import chat.liuxin.ai.common.client.TtsClient;
import chat.liuxin.ai.common.tts.AvatarCueService;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import chat.liuxin.ai.dto.AvatarCuePayload;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    private final ChatServiceHelper chatServiceHelper;
    private final TtsClient ttsClient;
    private final TtsSegmenter ttsSegmenter;
    private final AvatarCueService avatarCueService;
    private final AiChatProperties aiChatProperties;

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
                                        AiModelPolicy.ModelParameters params) {
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

        CompletableFuture.runAsync(() -> {
            Long convId = conversationId;
            if (!guestMode && convId == null) {
                convId = memoryService.createConversation(userIdStr, chatServiceHelper.generateTitle(input));
            }
            final Long finalConvId = convId;

            try {
                List<Message> messages = chatServiceHelper.prepareMessages(request, userIdStr, finalConvId, guestMode);
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
                }, 15, 15, TimeUnit.SECONDS);

                Flux<String> flux = siliconFlowChatClient.streamChat(messages, modelName, params.temperature(), params.maxTokens());
                boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());

                subscribeStream(emitter, flux, finalConvId, ttsEnabled, emitterClosed, ttsExecutorRef,
                        guestMode, userIdStr, modelName,
                        fullResponse -> {
                            if (!guestMode && finalConvId != null) {
                                memoryService.saveAssistantMessage(userIdStr, finalConvId, fullResponse, modelName, 1, null);
                            }
                        },
                        (partial, errorMsg) -> {
                            if (!guestMode && finalConvId != null && partial != null && !partial.isBlank()) {
                                memoryService.saveAssistantMessage(userIdStr, finalConvId, partial, modelName, 3, null);
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
     * 与看板娘流式的区别:走 WRITING 模式(注册 WritingTools)、不落库、无心跳线程。
     * 其他 SSE 生命周期、TTS 编排、AvatarCue 逻辑与 {@link #processStreamChat} 共用。
     */
    public SseEmitter processWritingStream(ChatRequest request, Long userId, String modelName,
                                           AiModelPolicy.ModelParameters params) {
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        Long conversationId = guestMode ? null : request.getConversationId();

        SseEmitter emitter = new SseEmitter(aiChatProperties.getSseTimeout());
        AtomicReference<ExecutorService> ttsExecutorRef = new AtomicReference<>();
        AtomicBoolean emitterClosed = new AtomicBoolean(false);

        emitter.onCompletion(() -> {
            emitterClosed.set(true);
            SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
        });
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
            emitter.complete();
        });

        CompletableFuture.runAsync(() -> {
            try {
                List<Message> messages = chatServiceHelper.prepareMessages(request, userIdStr, conversationId, guestMode);

                SseEmitterHelper.sendSseEvent(emitter, "start", SseEmitterHelper.eventPayload(
                        "conversationId", conversationId, "model", modelName, "mode", "writing"));

                Flux<String> flux = siliconFlowChatClient.streamChat(messages, modelName, params.temperature(), params.maxTokens(), SiliconFlowChatClient.ChatMode.WRITING);
                boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());

                subscribeStream(emitter, flux, conversationId, ttsEnabled, emitterClosed, ttsExecutorRef,
                        guestMode, userIdStr, modelName, fullResponse -> {}, (partial, errorMsg) -> {});

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
            java.util.function.Consumer<String> onComplete,
            java.util.function.BiConsumer<String, String> onError
    ) {
        AtomicReference<StringBuilder> fullResponseRef = new AtomicReference<>(new StringBuilder());
        StringBuilder textBuffer = new StringBuilder();
        AtomicInteger seq = new AtomicInteger(0);

        int poolSize = Math.max(1, aiChatProperties.getTtsStreamConcurrency());
        ExecutorService ttsExecutor = Executors.newFixedThreadPool(poolSize);
        ttsExecutorRef.set(ttsExecutor);
        List<CompletableFuture<Void>> ttsFutures = Collections.synchronizedList(new ArrayList<>());

        flux.subscribe(
                // ---- onNext ----
                chunk -> {
                    try {
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
                    String error_msg = error != null ? error.getMessage() : "未知错误";
                    onError.accept(fullResponseRef.get().toString(), error_msg);
                    SseEmitterHelper.safeSendError(emitter, conversationId, error_msg);
                    emitterClosed.set(true);
                    SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
                    emitter.completeWithError(error != null ? error : new RuntimeException("流式响应发生未知错误"));
                },
                // ---- onComplete ----
                () -> {
                    try {
                        String fullResponse = fullResponseRef.get().toString();
                        onComplete.accept(fullResponse);

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
                            CompletableFuture.runAsync(() -> {
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

}
