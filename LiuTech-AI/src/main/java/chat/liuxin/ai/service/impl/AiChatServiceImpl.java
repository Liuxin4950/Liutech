package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.common.client.TtsClient;
import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.common.tts.AvatarCueService;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import chat.liuxin.ai.dto.AvatarCuePayload;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.ChatResponse;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.SensitiveLogSanitizer;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.MemoryService;
import chat.liuxin.ai.service.PromptAssembler;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 聊天服务实现。
 *
 * <p>对外暴露四种语义：
 * <ul>
 *   <li>processChat / processWriting — 同步一次性返回</li>
 *   <li>processStreamChat / processWritingStream — SSE 流式返回（含 TTS + Live2D 表情）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    private final AiMetrics aiMetrics;
    private final TtsClient ttsClient;
    private final PromptAssembler promptAssembler;
    private final AiModelPolicy aiModelPolicy;
    private final SensitiveLogSanitizer sensitiveLogSanitizer;
    private final TtsSegmenter ttsSegmenter;
    private final AvatarCueService avatarCueService;

    @Value("${spring.ai.openai.chat.options.model:THUDM/glm-4-9b-chat}")
    private String defaultModel;

    @Value("${spring.ai.sse.timeout:120000}")
    private long sseTimeout;

    @Value("${tts.stream.concurrency:1}")
    private int ttsStreamConcurrency;

    // ==================== 同步接口 ====================

    @Override
    public ChatResponse processChat(ChatRequest request, Long userId) {
        long begin = System.currentTimeMillis();
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = resolveModelName(request);
        Long conversationId = guestMode ? null : request.getConversationId();

        try {
            List<Message> messages = prepareMessages(request, userIdStr, conversationId, guestMode);
            String input = request.getMessage();

            if (!guestMode && conversationId == null) {
                conversationId = memoryService.createConversation(userIdStr, generateTitle(input));
            }
            if (!guestMode) {
                memoryService.saveUserMessage(userIdStr, conversationId, input, modelName, null);
            }

            AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
            logParameterApplication(modelName, params);
            String aiOutput = siliconFlowChatClient.chat(messages, modelName, params.temperature(), params.maxTokens());

            if (!guestMode) {
                memoryService.saveAssistantMessage(userIdStr, conversationId, aiOutput, modelName, 1, null);
            }

            long cost = System.currentTimeMillis() - begin;
            aiMetrics.recordSuccess(modelName, cost, estimateTokens(aiOutput));
            return buildSuccessResponse(aiOutput, modelName, cost, conversationId, guestMode);

        } catch (AIServiceException e) {
            aiMetrics.recordFailure(modelName, System.currentTimeMillis() - begin, e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            aiMetrics.recordFailure(modelName, cost, e.getClass().getSimpleName());
            log.error("AI普通聊天失败", e);
            saveErrorIfNeeded(guestMode, userIdStr, conversationId, modelName);
            throw classifyException(e);
        }
    }

    @Override
    public ChatResponse processWriting(ChatRequest request, Long userId) {
        long begin = System.currentTimeMillis();
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = resolveModelName(request);
        Long conversationId = guestMode ? null : request.getConversationId();

        try {
            List<Message> messages = prepareMessages(request, userIdStr, conversationId, guestMode);
            AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
            logParameterApplication(modelName, params);
            String aiOutput = siliconFlowChatClient.chatForWriting(messages, modelName, params.temperature(), params.maxTokens());

            long cost = System.currentTimeMillis() - begin;
            aiMetrics.recordSuccess(modelName, cost, estimateTokens(aiOutput));
            return buildSuccessResponse(aiOutput, modelName, cost, conversationId, guestMode);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            aiMetrics.recordFailure(modelName, cost, e.getClass().getSimpleName());
            log.error("AI写作助手失败", e);
            return ChatResponse.builder().success(false)
                    .message("写作助手处理失败: " + e.getMessage())
                    .model(modelName).processingTime(cost).build();
        }
    }

    // ==================== 流式接口 ====================

    @Override
    public SseEmitter processStreamChat(ChatRequest request, Long userId) {
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = resolveModelName(request);
        Long conversationId = guestMode ? null : request.getConversationId();
        String input = request.getMessage();

        AtomicReference<ExecutorService> ttsExecutorRef = new AtomicReference<>();
        AtomicReference<ScheduledExecutorService> heartbeatRef = new AtomicReference<>();
        AtomicBoolean emitterClosed = new AtomicBoolean(false);

        SseEmitter emitter = new SseEmitter(sseTimeout);
        emitter.onCompletion(() -> {
            emitterClosed.set(true);
            shutdown(heartbeatRef.getAndSet(null), true);
            shutdown(ttsExecutorRef.getAndSet(null), true);
        });
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            shutdown(heartbeatRef.getAndSet(null), true);
            shutdown(ttsExecutorRef.getAndSet(null), true);
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

                AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
                logParameterApplication(modelName, params);

                sendSseEvent(emitter, "start", eventPayload(
                        "conversationId", finalConvId, "model", modelName, "mode", guestMode ? "guest" : "user"));

                // 心跳
                ScheduledExecutorService hb = Executors.newSingleThreadScheduledExecutor();
                heartbeatRef.set(hb);
                hb.scheduleAtFixedRate(() -> {
                    if (emitterClosed.get()) return;
                    try {
                        sendSseEvent(emitter, "heartbeat", eventPayload(
                                "conversationId", finalConvId, "timestamp", System.currentTimeMillis()));
                    } catch (Exception e) {
                        log.debug("心跳发送失败: {}", e.getMessage());
                    }
                }, 15, 15, TimeUnit.SECONDS);

                Flux<String> flux = siliconFlowChatClient.streamChat(messages, modelName, params.temperature(), params.maxTokens());
                boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());

                subscribeStream(emitter, flux, finalConvId, ttsEnabled, emitterClosed, ttsExecutorRef,
                        guestMode, userIdStr, modelName,
                        () -> {
                            if (!guestMode && finalConvId != null) {
                                // 保存完整回复在 onComplete 回调中通过 fullResponse 获取
                            }
                        },
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
                safeSendError(emitter, finalConvId, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public SseEmitter processWritingStream(ChatRequest request, Long userId) {
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = resolveModelName(request);
        Long conversationId = guestMode ? null : request.getConversationId();

        AtomicReference<ExecutorService> ttsExecutorRef = new AtomicReference<>();
        AtomicBoolean emitterClosed = new AtomicBoolean(false);

        SseEmitter emitter = new SseEmitter(sseTimeout);
        emitter.onCompletion(() -> {
            emitterClosed.set(true);
            shutdown(ttsExecutorRef.getAndSet(null), true);
        });
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            shutdown(ttsExecutorRef.getAndSet(null), true);
            emitter.complete();
        });

        CompletableFuture.runAsync(() -> {
            try {
                List<Message> messages = prepareMessages(request, userIdStr, conversationId, guestMode);
                AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
                logParameterApplication(modelName, params);

                sendSseEvent(emitter, "start", eventPayload(
                        "conversationId", conversationId, "model", modelName, "mode", "writing"));

                Flux<String> flux = siliconFlowChatClient.streamChatForWriting(messages, modelName, params.temperature(), params.maxTokens());
                boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());

                subscribeStream(emitter, flux, conversationId, ttsEnabled, emitterClosed, ttsExecutorRef,
                        guestMode, userIdStr, modelName, () -> {}, fullResponse -> {}, errorMsg -> {});

            } catch (Exception e) {
                log.error("写作助手流式处理失败，用户ID: {}, 会话ID: {}", userIdStr, conversationId, e);
                safeSendError(emitter, conversationId, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ==================== 流式订阅核心（TTS + AvatarCue + SSE） ====================

    /**
     * 统一的流式订阅逻辑。
     *
     * <p>职责：
     * <ol>
     *   <li>逐块收集完整回复</li>
     *   <li>按标点切分文本段，每段生成 Live2D 表情提示（avatar-cue）</li>
     *   <li>若 TTS 开启，异步合成音频并推送 audio 事件（seq 与 avatar-cue 保序）</li>
     *   <li>流结束时收尾：flush 剩余文本、等待 TTS、发送 audio-complete</li>
     * </ol>
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
            Runnable onStart,
            java.util.function.Consumer<String> onComplete,
            java.util.function.Consumer<String> onError
    ) {
        AtomicReference<StringBuilder> fullResponseRef = new AtomicReference<>(new StringBuilder());
        StringBuilder textBuffer = new StringBuilder();
        AtomicInteger seq = new AtomicInteger(0);

        int poolSize = Math.max(1, ttsStreamConcurrency);
        ExecutorService ttsExecutor = Executors.newFixedThreadPool(poolSize);
        ttsExecutorRef.set(ttsExecutor);
        List<CompletableFuture<Void>> ttsFutures = Collections.synchronizedList(new ArrayList<>());

        emitter.onCompletion(() -> {
            emitterClosed.set(true);
            shutdown(ttsExecutorRef.getAndSet(null), true);
        });

        onStart.run();

        flux.subscribe(
                // ---- onNext ----
                chunk -> {
                    try {
                        fullResponseRef.get().append(chunk);
                        textBuffer.append(chunk);

                        List<String> segments = ttsSegmenter.extractSegments(textBuffer, seq.get() > 0);
                        for (String seg : segments) {
                            int currentSeq = seq.incrementAndGet();
                            // avatar-cue 始终发送（TTS 开关解耦）
                            sendAvatarCue(emitter, currentSeq, conversationId, seg);
                            if (ttsEnabled) {
                                enqueueTtsTask(emitter, ttsExecutor, ttsFutures, currentSeq, conversationId, seg);
                            }
                        }

                        sendSseEvent(emitter, "data", eventPayload(
                                "content", chunk, "conversationId", conversationId));
                    } catch (IOException e) {
                        log.error("发送SSE事件失败", e);
                        emitter.completeWithError(e);
                    }
                },
                // ---- onError ----
                error -> {
                    log.error("流式响应错误，用户ID: {}, 会话ID: {}", userIdStr, conversationId, error);
                    onError.accept(error != null ? error.getMessage() : "未知错误");
                    safeSendError(emitter, conversationId, error != null ? error.getMessage() : "未知错误");
                    emitterClosed.set(true);
                    shutdown(ttsExecutorRef.getAndSet(null), true);
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

                        sendSseEvent(emitter, "complete", eventPayload(
                                "conversationId", conversationId,
                                "responseLength", fullResponse.length(),
                                "mode", guestMode ? "guest" : "user",
                                "ttsEnabled", ttsEnabled));

                        if (ttsEnabled && !ttsFutures.isEmpty()) {
                            // 等待所有 TTS 完成后再关闭
                            CompletableFuture.runAsync(() -> {
                                boolean timedOut = false;
                                try {
                                    CompletableFuture.allOf(ttsFutures.toArray(new CompletableFuture[0]))
                                            .get(Math.max(30_000L, sseTimeout), TimeUnit.MILLISECONDS);
                                } catch (TimeoutException e) {
                                    timedOut = true;
                                } catch (Exception e) {
                                    log.warn("等待TTS完成异常: {}", e.getMessage());
                                }
                                try {
                                    sendSseEvent(emitter, "audio-complete", eventPayload(
                                            "conversationId", conversationId,
                                            "timedOut", timedOut,
                                            "segments", seq.get()));
                                } catch (Exception ignore) {}
                                emitterClosed.set(true);
                                shutdown(ttsExecutorRef.getAndSet(null), false);
                                emitter.complete();
                            });
                            return; // 不在这里 complete，由异步任务负责
                        }

                        emitterClosed.set(true);
                        shutdown(ttsExecutorRef.getAndSet(null), true);
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("完成流式响应时发生错误", e);
                        emitterClosed.set(true);
                        shutdown(ttsExecutorRef.getAndSet(null), true);
                        emitter.completeWithError(e);
                    }
                }
        );
    }

    // ==================== TTS / AvatarCue ====================

    /** 发送 Live2D 表情提示事件 */
    private void sendAvatarCue(SseEmitter emitter, int seq, Long conversationId, String text) {
        try {
            AvatarCuePayload cue = avatarCueService.fromText(seq, conversationId, text);
            sendSseEvent(emitter, "avatar-cue", eventPayload(
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

    /** 提交一段 TTS 推理任务，完成后推送 audio 事件 */
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
                    sendSseEvent(emitter, "audio-skip", eventPayload(
                            "seq", seq, "text", segment, "reason", "empty-audio-url", "conversationId", conversationId));
                    return;
                }
                sendSseEvent(emitter, "audio", eventPayload(
                        "seq", seq, "text", segment, "audioUrl", audioUrl, "conversationId", conversationId));
            } catch (Exception e) {
                try {
                    sendSseEvent(emitter, "audio-skip", eventPayload(
                            "seq", seq, "text", segment, "reason", e.getClass().getSimpleName(), "conversationId", conversationId));
                } catch (Exception ignore) {}
            }
        }, executor);
        futures.add(task);
    }

    // ==================== 通用工具方法 ====================

    private List<Message> prepareMessages(ChatRequest request, String userId, Long conversationId, boolean guestMode) {
        List<Message> messages = promptAssembler.assemble(request, userId, conversationId, guestMode);
        messages.add(new UserMessage(request.getMessage()));
        return messages;
    }

    private ChatResponse buildSuccessResponse(String aiOutput, String modelName, long cost, Long conversationId, boolean guestMode) {
        return ChatResponse.builder()
                .success(true).message(aiOutput).model(modelName)
                .processingTime(cost).responseLength(aiOutput != null ? aiOutput.length() : 0)
                .conversationId(guestMode ? null : conversationId)
                .mode(guestMode ? "guest" : "user").build();
    }

    private String resolveModelName(ChatRequest request) {
        return aiModelPolicy.resolveModelName(request);
    }

    private AiModelPolicy.ModelParameters getModelParameters(ChatRequest request, String modelName) {
        return aiModelPolicy.resolveParameters(request, modelName);
    }

    private void logParameterApplication(String modelName, AiModelPolicy.ModelParameters params) {
        if (params.temperature() != null || params.maxTokens() != null) {
            log.info("AI模型参数 - 模型: {}, 来源: {}, temperature: {}, maxTokens: {}",
                    modelName, params.source(),
                    params.temperature() != null ? String.format("%.2f", params.temperature()) : "未设置",
                    params.maxTokens() != null ? params.maxTokens() : "未设置");
        } else {
            log.info("AI模型参数 - 模型: {}, 来源: {}, 使用默认参数", modelName, params.source());
        }
    }

    private String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) return "新会话";
        String trimmed = firstMessage.trim();
        return trimmed.length() > 10 ? trimmed.substring(0, 10) + "..." : trimmed;
    }

    private int estimateTokens(String text) {
        return text != null ? text.length() / 4 : 0;
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

    /** 将通用异常分类为 AIServiceException 子类 */
    private AIServiceException classifyException(Exception e) {
        Throwable root = e.getCause() != null ? e.getCause() : e;
        if (root instanceof java.net.ConnectException || root instanceof java.net.SocketTimeoutException
                || root instanceof java.net.UnknownHostException) {
            return new AIServiceException.ConnectionException("AI服务连接失败: " + root.getMessage());
        }
        if (root instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
            return new AIServiceException.RequestException("AI服务HTTP错误 " + httpEx.getStatusCode() + ": " + httpEx.getMessage());
        }
        if (root instanceof org.springframework.web.client.ResourceAccessException) {
            return new AIServiceException.ConnectionException("AI服务网络访问失败: " + root.getMessage());
        }
        if (root instanceof com.fasterxml.jackson.core.JsonParseException || root instanceof java.text.ParseException) {
            return new AIServiceException.ModelException("AI服务响应解析失败: " + root.getMessage());
        }
        if (root instanceof TimeoutException || (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
            return new AIServiceException.TimeoutException("AI服务响应超时: " + root.getMessage());
        }
        return new AIServiceException("AI服务处理异常: " + (root.getMessage() != null ? root.getMessage() : "未知错误"));
    }

    private Map<String, Object> eventPayload(Object... keyValues) {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }

    private void sendSseEvent(SseEmitter emitter, String event, Map<String, Object> data) throws IOException {
        Object safeData = data != null ? data : new HashMap<String, Object>();
        synchronized (emitter) {
            emitter.send(SseEmitter.event()
                    .name(event != null ? event : "unknown")
                    .data(safeData));
        }
    }

    private void safeSendError(SseEmitter emitter, Long conversationId, String errorMsg) {
        try {
            sendSseEvent(emitter, "error", eventPayload("conversationId", conversationId, "error", errorMsg));
        } catch (Exception ignore) {}
    }

    private void shutdown(ExecutorService executor, boolean immediate) {
        if (executor == null) return;
        try {
            if (immediate) executor.shutdownNow();
            else { executor.shutdown(); executor.awaitTermination(2, TimeUnit.SECONDS); }
        } catch (Exception ignore) {}
    }
}
