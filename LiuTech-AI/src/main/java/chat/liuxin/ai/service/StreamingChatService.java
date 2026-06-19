package chat.liuxin.ai.service;

import chat.liuxin.ai.common.client.TtsClient;
import chat.liuxin.ai.common.tts.AvatarCueService;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import chat.liuxin.ai.dto.AvatarCuePayload;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式聊天服务。
 *
 * <p>从 AiChatServiceImpl 中抽取，专门处理 SSE 流式响应，包含：
 * <ul>
 *   <li>看板聊天流式（processStreamChat）</li>
 *   <li>写作助手流式（processWritingStream）</li>
 *   <li>TTS 语音合成编排</li>
 *   <li>Live2D 表情提示（AvatarCue）</li>
 *   <li>SSE 心跳保活</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingChatService {

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    private final PromptService promptService;
    private final TtsClient ttsClient;
    private final TtsSegmenter ttsSegmenter;
    private final AvatarCueService avatarCueService;
    private final SseEmitterHelper sseHelper;
    private final AiChatProperties aiChatProperties;

    // ==================== 公开接口 ====================

    /**
     * 看板聊天流式：SSE 返回 + 消息持久化 + TTS + AvatarCue
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
            sseHelper.shutdown(heartbeatRef.getAndSet(null), true);
            sseHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
        });
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            sseHelper.shutdown(heartbeatRef.getAndSet(null), true);
            sseHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
            emitter.complete();
        });

        CompletableFuture.runAsync(() -> {
            Long convId = conversationId;
            if (!guestMode && convId == null) {
                convId = memoryService.createConversation(userIdStr, generateTitle(input));
            }
            final Long finalConvId = convId;

            try {
                List<Message> messages = prepareMessages(request, userIdStr, finalConvId, guestMode);
                if (!guestMode && finalConvId != null) {
                    memoryService.saveUserMessage(userIdStr, finalConvId, input, modelName, null);
                }

                sseHelper.sendSseEvent(emitter, "start", sseHelper.eventPayload(
                        "conversationId", finalConvId, "model", modelName, "mode", guestMode ? "guest" : "user"));

                ScheduledExecutorService hb = Executors.newSingleThreadScheduledExecutor();
                heartbeatRef.set(hb);
                hb.scheduleAtFixedRate(() -> {
                    if (emitterClosed.get()) return;
                    try {
                        sseHelper.sendSseEvent(emitter, "heartbeat", sseHelper.eventPayload(
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
                        errorMsg -> {
                            saveErrorIfNeeded(guestMode, userIdStr, finalConvId, modelName);
                        });

            } catch (Exception e) {
                log.error("流式聊天处理失败，用户ID: {}, 会话ID: {}", userIdStr, finalConvId, e);
                saveErrorIfNeeded(guestMode, userIdStr, finalConvId, modelName);
                sseHelper.safeSendError(emitter, finalConvId, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 写作助手流式：SSE 返回 + TTS + AvatarCue（不持久化消息）
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
            sseHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
        });
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            sseHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
            emitter.complete();
        });

        CompletableFuture.runAsync(() -> {
            try {
                List<Message> messages = prepareMessages(request, userIdStr, conversationId, guestMode);

                sseHelper.sendSseEvent(emitter, "start", sseHelper.eventPayload(
                        "conversationId", conversationId, "model", modelName, "mode", "writing"));

                Flux<String> flux = siliconFlowChatClient.streamChat(messages, modelName, params.temperature(), params.maxTokens(), SiliconFlowChatClient.ChatMode.WRITING);
                boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());

                subscribeStream(emitter, flux, conversationId, ttsEnabled, emitterClosed, ttsExecutorRef,
                        guestMode, userIdStr, modelName, fullResponse -> {}, errorMsg -> {});

            } catch (Exception e) {
                log.error("写作助手流式处理失败，用户ID: {}, 会话ID: {}", userIdStr, conversationId, e);
                sseHelper.safeSendError(emitter, conversationId, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ==================== 流式订阅核心（TTS + AvatarCue + SSE） ====================

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
            java.util.function.Consumer<String> onError
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

                        sseHelper.sendSseEvent(emitter, "data", sseHelper.eventPayload(
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
                    onError.accept(error_msg);
                    sseHelper.safeSendError(emitter, conversationId, error_msg);
                    emitterClosed.set(true);
                    sseHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
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

                        sseHelper.sendSseEvent(emitter, "complete", sseHelper.eventPayload(
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
                                    sseHelper.sendSseEvent(emitter, "audio-complete", sseHelper.eventPayload(
                                            "conversationId", conversationId,
                                            "timedOut", timedOut,
                                            "segments", seq.get()));
                                } catch (Exception ignore) {
                                }
                                emitterClosed.set(true);
                                sseHelper.shutdown(ttsExecutorRef.getAndSet(null), false);
                                emitter.complete();
                            });
                            return;
                        }

                        emitterClosed.set(true);
                        sseHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("完成流式响应时发生错误", e);
                        emitterClosed.set(true);
                        sseHelper.shutdown(ttsExecutorRef.getAndSet(null), true);
                        emitter.completeWithError(e);
                    }
                }
        );
    }

    // ==================== TTS / AvatarCue ====================

    private void sendAvatarCue(SseEmitter emitter, int seq, Long conversationId, String text) {
        try {
            AvatarCuePayload cue = avatarCueService.fromText(seq, conversationId, text);
            sseHelper.sendSseEvent(emitter, "avatar-cue", sseHelper.eventPayload(
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
                    sseHelper.sendSseEvent(emitter, "audio-skip", sseHelper.eventPayload(
                            "seq", seq, "text", segment, "reason", "empty-audio-url", "conversationId", conversationId));
                    return;
                }
                sseHelper.sendSseEvent(emitter, "audio", sseHelper.eventPayload(
                        "seq", seq, "text", segment, "audioUrl", audioUrl, "conversationId", conversationId));
            } catch (Exception e) {
                try {
                    sseHelper.sendSseEvent(emitter, "audio-skip", sseHelper.eventPayload(
                            "seq", seq, "text", segment, "reason", e.getClass().getSimpleName(), "conversationId", conversationId));
                } catch (Exception ignore) {
                }
            }
        }, executor);
        futures.add(task);
    }

    // ==================== 内部工具 ====================

    private List<Message> prepareMessages(ChatRequest request, String userId, Long conversationId, boolean guestMode) {
        List<Message> messages = promptService.assemble(request, userId, conversationId, guestMode, memoryService);
        messages.add(new UserMessage(request.getMessage() != null ? request.getMessage() : ""));
        return messages;
    }

    private String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) return "新会话";
        String trimmed = firstMessage.trim();
        return trimmed.length() > 10 ? trimmed.substring(0, 10) + "..." : trimmed;
    }

    private void saveErrorIfNeeded(boolean guestMode, String userId, Long conversationId, String modelName) {
        if (!guestMode && conversationId != null) {
            try {
                memoryService.saveAssistantMessage(userId, conversationId, null, modelName, 3, null);
            } catch (Exception e) {
                log.warn("记录错误消息失败: {}", e.getMessage());
            }
        }
    }
}
