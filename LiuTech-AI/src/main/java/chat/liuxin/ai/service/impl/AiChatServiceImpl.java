package chat.liuxin.ai.service.impl;

/**
 * AI聊天服务实现
 * 
 * 主要职责：
 * 提供AI聊天功能的核心实现，处理用户输入并生成AI回复
 * 
 * 业务位置：
 * 位于AI服务层，是用户请求与AI模型交互的核心桥梁
 * 
 * 核心功能点：
 * 1. 处理普通聊天请求，一次性返回完整AI回复
 * 2. 管理会话上下文，维护历史消息记录
 * 3. 构建AI模型请求的消息序列
 * 4. 异常处理和错误恢复机制
 * 5. 性能监控和日志记录
 * 
 * 作者：刘鑫
 * 时间：2025-12-01
 */

import chat.liuxin.ai.exception.AIServiceException;
import chat.liuxin.ai.client.TtsClient;
import chat.liuxin.ai.monitor.AiMetrics;
import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.dto.ModelConfigDTO;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.AiModelConfigService;
import chat.liuxin.ai.service.BlogContextService;
import chat.liuxin.ai.service.MemoryService;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    private final AiMetrics aiMetrics;
    private final BlogContextService blogContextService;
    private final AiModelConfigService aiModelConfigService;
    private final TtsClient ttsClient;

    @Value("${spring.ai.chat.history-limit:19}")
    private int historyLimit; // 历史条数（不含本轮输入）

    @Value("${spring.ai.openai.chat.options.model:THUDM/glm-4-9b-chat}")
    private String defaultModel; // 默认AI模型名称

    @Value("${spring.ai.sse.timeout:120000}")
    private long sseTimeout; // SSE超时时间（毫秒），默认2分钟

    @Value("${tts.stream.concurrency:1}")
    private int ttsStreamConcurrency;

    /**
     * 处理普通聊天请求，一次性返回完整AI回复
     * 
     * 业务流程：
     * 1. 初始化请求参数和性能监控
     * 2. 加载会话历史上下文
     * 3. 构建AI模型请求的消息序列
     * 4. 处理会话管理（新建或复用）
     * 5. 调用AI模型生成回复
     * 6. 保存交互记录并返回结果
     * 
     * @param request 聊天请求对象，包含用户输入和会话信息
     * @param userId 用户ID，用于会话关联
     * @return 聊天响应对象，包含AI回复和元数据
     */
    @Override
    public ChatResponse processChat(ChatRequest request, Long userId) {
        long begin = System.currentTimeMillis();
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = request.getModel() != null ? request.getModel() : defaultModel;
        Long conversationId = guestMode ? null : request.getConversationId();
        
        try {
            String input = request.getMessage();
            List<Message> messages = buildPromptMessages(request, userIdStr, conversationId, guestMode);
            messages.add(new UserMessage(input));

            if (!guestMode && conversationId == null) {
                String title = "新会话";
                conversationId = memoryService.createConversation(userIdStr, title);
            }

            if (!guestMode) {
                memoryService.saveUserMessage(userIdStr, conversationId, input, modelName, null);
            }

            ModelParameters params = getModelParameters(request, modelName);
            logParameterApplication(modelName, params);

            String aiOutput = siliconFlowChatClient.chat(messages, modelName, params.temperature, params.maxTokens);
            if (!guestMode) {
                memoryService.saveAssistantMessage(userIdStr, conversationId, aiOutput, modelName, 1, null);
            }

            long cost = System.currentTimeMillis() - begin;
            log.debug("AI普通聊天成功，模型:{}，输入长度:{}，输出长度:{}，耗时:{}ms", modelName, input.length(),
                    aiOutput != null ? aiOutput.length() : 0, cost);

            int tokenCount = aiOutput != null ? aiOutput.length() / 4 : 0; // 粗略估算Token数
            aiMetrics.recordSuccess(modelName, cost, tokenCount);

            return ChatResponse.builder()
                    .success(true)
                    .message(aiOutput)
                    .emotion(null)
                    .action(null)
                    .model(modelName)
                    .processingTime(cost)
                    .responseLength(aiOutput != null ? aiOutput.length() : 0)
                    .conversationId(guestMode ? null : conversationId)
                    .mode(guestMode ? "guest" : "user")
                    .build();

        } catch (AIServiceException e) {
            long cost = System.currentTimeMillis() - begin;
            String errorType = e.getClass().getSimpleName();
            aiMetrics.recordFailure(modelName, cost, errorType);
            log.error("AI服务异常: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            aiMetrics.recordFailure(modelName, cost, e.getClass().getSimpleName());
            log.error("AI普通聊天失败", e);
            try {
                if (!guestMode && conversationId != null) {
                    memoryService.saveAssistantMessage(userIdStr, conversationId, null, modelName, 9, null);
                }
            } catch (Exception ignore) {
                log.warn("记录AI错误消息失败: {}", ignore.getMessage());
            }

            Throwable cause = e.getCause();
            Throwable rootCause = cause != null ? cause : e;

            // 网络连接异常
            if (rootCause instanceof java.net.ConnectException ||
                    rootCause instanceof java.net.SocketTimeoutException ||
                    rootCause instanceof java.net.UnknownHostException) {
                throw new AIServiceException.ConnectionException("AI服务连接失败: " + rootCause.getMessage());
            }
            // HTTP状态码异常
            else if (rootCause instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
                throw new AIServiceException.RequestException("AI服务HTTP错误 " + httpEx.getStatusCode() + ": " + httpEx.getMessage());
            }
            // 网络访问异常
            else if (rootCause instanceof org.springframework.web.client.ResourceAccessException) {
                throw new AIServiceException.ConnectionException("AI服务网络访问失败: " + rootCause.getMessage());
            }
            // 响应解析异常
            else if (rootCause instanceof com.fasterxml.jackson.core.JsonParseException ||
                    rootCause instanceof java.text.ParseException) {
                throw new AIServiceException.ModelException("AI服务响应解析失败: " + rootCause.getMessage());
            }
            // 超时异常
            else if (rootCause instanceof java.util.concurrent.TimeoutException ||
                    (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
                throw new AIServiceException.TimeoutException("AI服务响应超时: " + rootCause.getMessage());
            }
            // 其他未知异常
            else {
                throw new AIServiceException("AI服务处理异常: " + (rootCause.getMessage() != null ? rootCause.getMessage() : "未知错误"));
            }
        }
    }
    
    /**
     * 处理流式聊天请求，通过SSE推送AI回复流
     * 
     * 业务流程：
     * 1. 创建SseEmitter并设置超时和完成/错误处理
     * 2. 异步处理聊天请求，避免阻塞主线程
     * 3. 发送开始事件，标识流开始
     * 4. 逐块发送AI响应数据
     * 5. 发送完成事件，标识流结束
     * 6. 在完成或错误时保存完整消息到数据库
     * 7. 设置请求属性标记流已完成，避免Security权限检查
     * 
     * @param request 聊天请求对象，包含用户输入和会话信息
     * @param userId 用户ID，用于会话关联
     * @return SseEmitter对象，用于推送流式响应
     */
    @Override
    public SseEmitter processStreamChat(ChatRequest request, Long userId) {
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = request.getModel() != null ? request.getModel() : defaultModel;
        Long conversationId = guestMode ? null : request.getConversationId();
        String input = request.getMessage();

        // 2. 创建SseEmitter，使用可配置的超时时间
        SseEmitter emitter = new SseEmitter(sseTimeout);
        
        // 3. 设置完成和错误处理
        emitter.onCompletion(() -> {
            log.debug("流式聊天完成，用户ID: {}, 会话ID: {}", userIdStr, conversationId);
        });
        emitter.onTimeout(() -> {
            log.warn("流式聊天超时，用户ID: {}, 会话ID: {}", userIdStr, conversationId);
            emitter.complete();
        });
        
        // 4. 异步处理聊天请求
        CompletableFuture.runAsync(() -> {
            Long currentConversationId = conversationId;
            if (!guestMode && currentConversationId == null) {
                String title = "新会话";
                currentConversationId = memoryService.createConversation(userIdStr, title);
            }
            final Long finalConversationId = currentConversationId;
            
            try {
                List<Message> messages = buildPromptMessages(request, userIdStr, conversationId, guestMode);
                messages.add(new UserMessage(input));
                
                if (!guestMode && finalConversationId != null) {
                    memoryService.saveUserMessage(userIdStr, finalConversationId, input, modelName, null);
                }

                // 4.5 获取并验证模型参数
                ModelParameters params = getModelParameters(request, modelName);
                logParameterApplication(modelName, params);

                // 4.6 发送开始事件
                sendSseEvent(emitter, "start", eventPayload(
                    "conversationId", finalConversationId,
                    "model", modelName,
                    "mode", guestMode ? "guest" : "user"
                ));

                // 4.7 用于收集完整响应的容器
                AtomicReference<StringBuilder> fullResponseRef = new AtomicReference<>(new StringBuilder());
                // 是否启用语音：由前端开关决定（true 才会在流式过程中触发 TTS 推理）
                boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());
                // TTS 文本缓冲区：流式 chunk 持续追加，按规则切分后触发推理
                StringBuilder ttsBuffer = new StringBuilder();
                // 音频序号：确保前端可以按 seq 严格顺序播放（避免并发导致乱序）
                AtomicInteger ttsSeq = new AtomicInteger(0);
                // TTS 触发并发度：默认 1（优先保证首段稳定），可通过配置调整
                int poolSize = Math.max(1, ttsStreamConcurrency);
                ExecutorService ttsExecutor = Executors.newFixedThreadPool(poolSize);
                // 用于在流结束时等待已提交的 TTS 推理任务，避免提前 close SSE 导致音频丢失
                List<CompletableFuture<Void>> ttsFutures = Collections.synchronizedList(new ArrayList<>());

                // 4.8 调用流式AI接口（传递模型参数）
                Flux<String> responseFlux = siliconFlowChatClient.streamChat(messages, modelName, params.temperature, params.maxTokens);
                
                // 4.8 订阅流式响应并处理每个数据块
                responseFlux.subscribe(
                    chunk -> {
                        try {
                            // 收集完整响应
                            fullResponseRef.get().append(chunk);
                            if (ttsEnabled) {
                                ttsBuffer.append(chunk);
                                // 根据切分规则从缓冲区提取“可发送给 TTS 的语音片段”
                                List<String> segments = extractTtsSegments(ttsBuffer);
                                if (!segments.isEmpty()) {
                                    for (String seg : segments) {
                                        enqueueTtsTask(emitter, ttsExecutor, ttsFutures, ttsSeq, finalConversationId, seg);
                                    }
                                }
                            }
                            
                            // 发送数据块
                            sendSseEvent(emitter, "data", eventPayload(
                                "content", chunk,
                                "conversationId", finalConversationId
                            ));
                        } catch (IOException e) {
                            log.error("发送SSE事件失败", e);
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        // 处理错误
                        log.error("流式响应错误，用户ID: {}, 会话ID: {}", userIdStr, finalConversationId, error);
                        try {
                            try {
                                ttsExecutor.shutdownNow();
                            } catch (Exception ignore) {
                            }
                            if (!guestMode && finalConversationId != null) {
                                memoryService.saveAssistantMessage(userIdStr, finalConversationId, null, modelName, 9, null);
                            }
                            sendSseEvent(emitter, "error", eventPayload(
                                "conversationId", finalConversationId,
                                "error", error.getMessage()
                            ));
                        } catch (Exception ex) {
                            log.error("发送错误事件失败", ex);
                        }
                        emitter.completeWithError(error != null ? error : new RuntimeException("流式响应发生未知错误"));
                    },
                    () -> {
                        // 处理完成
                        try {
                            // 获取完整响应
                            String fullResponse = fullResponseRef.get().toString();

                            if (ttsEnabled) {
                                // 流结束时再做一次切分/收尾：避免最后一段因为时机问题没切出来
                                List<String> tailSegments = extractTtsSegments(ttsBuffer);
                                if (!tailSegments.isEmpty()) {
                                    for (String seg : tailSegments) {
                                        enqueueTtsTask(emitter, ttsExecutor, ttsFutures, ttsSeq, finalConversationId, seg);
                                    }
                                }

                                String rest = ttsBuffer.toString().trim();
                                ttsBuffer.setLength(0);
                                if (!rest.isEmpty()) {
                                    enqueueTtsTask(emitter, ttsExecutor, ttsFutures, ttsSeq, finalConversationId, rest);
                                }

                                CompletableFuture<?>[] arr = ttsFutures.toArray(new CompletableFuture[0]);
                                CompletableFuture<Void> all = CompletableFuture.allOf(arr);
                                final String fullResponseFinal = fullResponse;
                                // 关键点：不能在这里立刻 emitter.complete()
                                // 否则后续 TTS 推理完成后发送 audio 会失败，导致“文本完整但语音只播一半”
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        long waitMs = Math.max(30_000L, sseTimeout);
                                        try {
                                            all.get(waitMs, TimeUnit.MILLISECONDS);
                                        } catch (TimeoutException ignore) {
                                        } catch (Exception ignore) {
                                        }
                                        try {
                                            ttsExecutor.shutdown();
                                            ttsExecutor.awaitTermination(2, TimeUnit.SECONDS);
                                        } catch (Exception ignore) {
                                        }

                                        if (!guestMode && finalConversationId != null) {
                                            memoryService.saveAssistantMessage(userIdStr, finalConversationId, fullResponseFinal, modelName, 1, null);
                                        }

                                        try {
                                            sendSseEvent(emitter, "complete", eventPayload(
                                                    "conversationId", finalConversationId,
                                                    "responseLength", fullResponseFinal.length(),
                                                    "mode", guestMode ? "guest" : "user"
                                            ));
                                        } catch (Exception ignore) {
                                        }
                                        emitter.complete();
                                    } catch (Exception e) {
                                        emitter.completeWithError(e);
                                    }
                                });
                                return;
                            }

                            try {
                                ttsExecutor.shutdownNow();
                            } catch (Exception ignore) {
                            }

                            if (!guestMode && finalConversationId != null) {
                                memoryService.saveAssistantMessage(userIdStr, finalConversationId, fullResponse, modelName, 1, null);
                            }

                            sendSseEvent(emitter, "complete", eventPayload(
                                    "conversationId", finalConversationId,
                                    "responseLength", fullResponse.length(),
                                    "mode", guestMode ? "guest" : "user"
                            ));

                            emitter.complete();
                        } catch (Exception e) {
                            log.error("完成流式响应时发生错误", e);
                            emitter.completeWithError(e);
                        }
                    }
                );
                
            } catch (Exception e) {
                log.error("流式聊天处理失败，用户ID: {}, 会话ID: {}", userIdStr, finalConversationId, e);
                // 在异常情况下，使用已创建的会话ID或创建新的异常会话
                Long errorConversationId = finalConversationId;
                if (!guestMode && errorConversationId == null) {
                    try {
                        errorConversationId = memoryService.createConversation(userIdStr, "异常会话");
                    } catch (Exception ex) {
                        log.error("创建异常会话失败", ex);
                        errorConversationId = 0L; // 使用默认值
                    }
                }
                final Long finalErrorConversationId = errorConversationId;
                
                try {
                    if (!guestMode && finalErrorConversationId != null && finalErrorConversationId > 0) {
                        memoryService.saveAssistantMessage(userIdStr, finalErrorConversationId, null, modelName, 9, null);
                    }
                    
                    sendSseEvent(emitter, "error", eventPayload(
                        "conversationId", finalErrorConversationId,
                        "error", e.getMessage()
                    ));
                } catch (Exception ex) {
                    log.error("发送错误事件失败", ex);
                }
                
                // 完成流并传递错误
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    /**
     * 提交一段 TTS 推理任务，并在完成后通过 SSE 推送 audio 事件
     *
     * 说明：
     * - 该任务异步执行（可能并发），但 seq 严格递增
     * - 前端应当按 seq 顺序播放，避免乱序
     * - 对纯标点/纯表情等不可读文本直接跳过，减少无意义推理
     */
    private void enqueueTtsTask(
            SseEmitter emitter,
            ExecutorService ttsExecutor,
            List<CompletableFuture<Void>> futures,
            AtomicInteger seq,
            Long conversationId,
            String text
    ) {
        String segment = text == null ? "" : text.trim();
        if (segment.isEmpty()) return;
        if (!containsSpeakableText(segment)) return;

        int currentSeq = seq.incrementAndGet();
        CompletableFuture<Void> next = CompletableFuture.runAsync(() -> {
            try {
                String audioUrl = ttsClient.inferSingleAudioUrl(segment);
                if (audioUrl == null || audioUrl.isBlank()) return;
                sendSseEvent(emitter, "audio", eventPayload(
                        "seq", currentSeq,
                        "text", segment,
                        "audioUrl", audioUrl,
                        "conversationId", conversationId
                ));
            } catch (Exception ignore) {
            }
        }, ttsExecutor);
        if (futures != null) {
            futures.add(next);
        }
    }

    /**
     * 判断片段是否包含“可读文本”
     *
     * 用途：过滤掉纯 emoji / 纯标点 / 纯空白，避免 TTS 推理浪费与播放异常。
     */
    private boolean containsSpeakableText(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) return true;
            Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
            if (b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || b == Character.UnicodeBlock.HIRAGANA
                    || b == Character.UnicodeBlock.KATAKANA
                    || b == Character.UnicodeBlock.HANGUL_SYLLABLES) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从缓冲区提取若干段可用于 TTS 推理的文本片段，并从 buffer 中删除已提取内容
     *
     * 切分规则（自用优化版）：
     * - 必须累计 >= 20 字才允许触发“按标点切分”
     * - 20~45 字范围内命中标点：在标点处分段
     * - 若一直没有标点：累计到 45 字强制切一段
     */
    private List<String> extractTtsSegments(StringBuilder buffer) {
        List<String> out = new ArrayList<>();
        if (buffer == null || buffer.length() == 0) return out;

        final int minSendLen = 20;
        final int hardCutLen = 45;

        while (true) {
            int len = buffer.length();
            if (len < minSendLen) break;

            int cut = -1;
            int scanLen = Math.min(len, hardCutLen);
            int lastPuncBeforeLimit = -1;
            for (int i = 0; i < scanLen; i++) {
                char c = buffer.charAt(i);
                if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n' || c == '!' || c == '?' || c == ';') {
                    lastPuncBeforeLimit = i + 1;
                }
            }

            if (lastPuncBeforeLimit >= minSendLen) {
                cut = lastPuncBeforeLimit;
            } else if (len >= hardCutLen) {
                cut = hardCutLen;
            }

            if (cut <= 0) break;

            String seg = buffer.substring(0, cut).trim();
            buffer.delete(0, cut);
            if (!seg.isEmpty()) out.add(seg);
        }

        return out;
    }

    private List<Message> buildPromptMessages(ChatRequest request, String userId, Long conversationId, boolean guestMode) {
        List<Message> messages = new ArrayList<>();

        String contextPrompt = blogContextService.buildContextPrompt(request.getContext());
        if (contextPrompt != null && !contextPrompt.isEmpty()) {
            messages.add(new SystemMessage(contextPrompt));
            log.debug("注入博客上下文: {} 字符", contextPrompt.length());
        }

        if (guestMode) {
            messages.addAll(buildGuestPromptMessages(request));
            return messages;
        }

        if (conversationId != null) {
            messages.addAll(memoryService.listLastMessagesAsPromptMessages(userId, conversationId, historyLimit));
        }

        return messages;
    }

    private List<Message> buildGuestPromptMessages(ChatRequest request) {
        if (request.getTempMessages() == null || request.getTempMessages().isEmpty()) {
            return Collections.emptyList();
        }

        int start = Math.max(0, request.getTempMessages().size() - 20);
        List<Message> messages = new ArrayList<>();
        for (ChatRequest.TempMessage tempMessage : request.getTempMessages().subList(start, request.getTempMessages().size())) {
            if (tempMessage == null || tempMessage.getContent() == null || tempMessage.getContent().isBlank()) {
                continue;
            }
            String role = tempMessage.getRole() == null ? "user" : tempMessage.getRole().trim().toLowerCase(Locale.ROOT);
            switch (role) {
                case "assistant" -> messages.add(new AssistantMessage(tempMessage.getContent()));
                case "system" -> messages.add(new SystemMessage(tempMessage.getContent()));
                default -> messages.add(new UserMessage(tempMessage.getContent()));
            }
        }
        return messages;
    }

    private Map<String, Object> eventPayload(Object... keyValues) {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }
    
    /**
     * 发送SSE事件
     *
     * @param emitter SseEmitter对象
     * @param event 事件类型（start, data, complete, error）
     * @param data 事件数据
     * @throws IOException 当发送失败时抛出
     */
    private void sendSseEvent(SseEmitter emitter, String event, Map<String, Object> data) throws IOException {
        Object safeData = data != null ? data : new java.util.HashMap<String, Object>();
        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event()
                .name(event != null ? event : "unknown")
                .data(safeData);
        synchronized (emitter) {
            emitter.send(eventBuilder);
        }
        log.debug("发送SSE事件: {}, 数据: {}", event, data);
    }

    /**
     * 模型参数封装类
     */
    private static class ModelParameters {
        Double temperature;
        Integer maxTokens;
        String source; // 参数来源：request/db/default

        ModelParameters(Double temperature, Integer maxTokens, String source) {
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.source = source;
        }
    }

    /**
     * 获取并验证模型参数
     *
     * 优先级：前端传递 > 数据库配置 > 默认值
     *
     * @param request 聊天请求
     * @param modelName 模型名称
     * @return 模型参数对象
     */
    private ModelParameters getModelParameters(ChatRequest request, String modelName) {
        Double temperature = request.getTemperature();
        Integer maxTokens = request.getMaxTokens();
        String source = "request"; // 默认来源为前端请求

        // 1. 验证前端传递的参数
        if (temperature != null) {
            if (temperature < 0.0 || temperature > 1.0) {
                log.warn("前端传递的 temperature 超出范围 [0.0, 1.0]: {}, 将忽略", temperature);
                temperature = null;
            }
        }
        if (maxTokens != null && maxTokens <= 0) {
            log.warn("前端传递的 maxTokens 无效: {}, 将忽略", maxTokens);
            maxTokens = null;
        }

        // 2. 如果前端没有传递完整参数，尝试从数据库读取
        if (temperature == null || maxTokens == null) {
            try {
                Optional<ModelConfigDTO> modelConfig = aiModelConfigService.getModelByName(modelName);
                if (modelConfig.isPresent()) {
                    ModelConfigDTO config = modelConfig.get();

                    // 合并配置（前端优先）
                    if (temperature == null && config.getTemperature() != null) {
                        temperature = config.getTemperature().doubleValue();
                    }
                    if (maxTokens == null && config.getMaxTokens() != null) {
                        maxTokens = config.getMaxTokens();
                    }

                    source = "database";
                    log.debug("从数据库读取模型配置: {}, temperature: {}, maxTokens: {}",
                            modelName, temperature, maxTokens);
                } else {
                    log.info("模型 {} 未在数据库中配置，使用AI提供商默认参数", modelName);
                    source = "default";
                }
            } catch (Exception e) {
                log.error("读取模型配置失败，模型: {}, 错误: {}, 将使用默认参数",
                        modelName, e.getMessage());
                source = "default";
            }
        }

        // 3. 最终参数来源确认
        if (request.getTemperature() != null && temperature != null &&
                !temperature.equals(request.getTemperature())) {
            source = "mixed"; // 前端和数据库混合
        }

        return new ModelParameters(temperature, maxTokens, source);
    }

    /**
     * 记录参数应用日志
     *
     * @param modelName 模型名称
     * @param params 模型参数
     */
    private void logParameterApplication(String modelName, ModelParameters params) {
        if (params.temperature != null || params.maxTokens != null) {
            log.info("AI模型参数应用 - 模型: {}, 来源: {}, temperature: {}, maxTokens: {}",
                    modelName,
                    params.source,
                    params.temperature != null ? String.format("%.2f", params.temperature) : "未设置",
                    params.maxTokens != null ? params.maxTokens : "未设置");
        } else {
            log.info("AI模型参数应用 - 模型: {}, 来源: {}, 使用AI提供商默认参数", modelName, params.source);
        }
    }
}
