package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.application.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.dto.AvatarCuePayload;
import chat.liuxin.ai.common.client.TtsClient;
import chat.liuxin.ai.common.tts.AvatarCueService;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 语音和表情事件编排。
 *
 * 职责：
 * - 将 AI 回复文本分段
 * - 为每段生成表情提示（avatar-cue）
 * - 并行生成 TTS 音频（通过 CompletableFuture + 专用线程池）
 * - 发送 audio-complete 结束标记
 *
 * 事件顺序：
 * - avatar-cue 与音频按段顺序发送（seq 保序）
 * - TTS 调用并行执行，但事件仍按 seq 顺序发送
 * - avatar-cue 与 TTS 开关解耦，TTS 关闭时仍发送表情
 *
 * 性能优化：
 * - TTS 串行调用是最大性能瓶颈（5-10 秒延迟）
 * - 改为 CompletableFuture 并行调用，多段音频同时合成
 * - 使用 agentExecutor 专用线程池，避免 ForkJoinPool 饥饿
 *
 * @author liuxin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechOrchestrationService {

    private final TtsSegmenter ttsSegmenter;
    private final AvatarCueService avatarCueService;
    private final TtsClient ttsClient;
    private final AgentStreamPublisher streamPublisher;

    /** Agent 专用线程池，用于并行 TTS 调用 */
    @Qualifier("agentExecutor")
    private final Executor agentExecutor;

    /**
     * 发布语音和表情事件（并行 TTS 版本）。
     *
     * 优化前：串行调用 TTS，多段音频总延迟 = 各段延迟之和
     * 优化后：并行调用 TTS，多段音频总延迟 ≈ 最慢一段的延迟
     *
     * @param context   SSE 上下文
     * @param request   请求（读取 ttsEnabled 标志）
     * @param response  响应（读取 message 文本）
     */
    public void publishSpeechAndCues(AgentSseContext context, AgentChatRequest request, AgentChatResponse response) {
        String message = response == null ? null : response.getMessage();
        if (message == null || message.isBlank()) {
            if (Boolean.TRUE.equals(request.getTtsEnabled())) {
                streamPublisher.sendAudioComplete(context.getEmitter(), context.getTaskId(), context.getConversationId(), 0, false);
            }
            return;
        }

        // 1. 按标点符号切句
        List<String> segments = ttsSegmenter.splitAll(message);
        if (segments.isEmpty() && ttsSegmenter.containsSpeakableText(message)) {
            segments = List.of(message.trim());
        }

        boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());

        // 2. 同步发送所有 avatar-cue（快速操作，不阻塞）
        int seq = 0;
        List<String> validSegments = new ArrayList<>();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) continue;
            seq++;
            validSegments.add(segment);
            AvatarCuePayload cue = avatarCueService.fromText(seq, context.getConversationId(), segment);
            streamPublisher.sendAvatarCue(context.getEmitter(), context.getTaskId(), context.getConversationId(), cue);
        }

        // 3. 并行调用 TTS 合成音频
        if (ttsEnabled && !validSegments.isEmpty()) {
            parallelTtsAndSend(context, validSegments);
        }

        // 4. 发送 audio-complete 结束标记
        if (ttsEnabled) {
            streamPublisher.sendAudioComplete(context.getEmitter(), context.getTaskId(), context.getConversationId(), validSegments.size(), false);
        }
    }

    /**
     * 并行调用 TTS 合成音频并发送事件。
     *
     * 策略：
     * - 每段 TTS 调用提交到 agentExecutor 线程池
     * - CompletableFuture.allOf 等待所有段完成
     * - 每段独立 try-catch，失败不影响其他段
     * - 使用 seq 编号保证事件发送顺序
     */
    private void parallelTtsAndSend(AgentSseContext context, List<String> segments) {
        // 为每段创建 CompletableFuture
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            final int seq = i + 1;
            final String segment = segments.get(i);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // 调用 TTS 合成
                    String audioUrl = ttsClient.inferSingleAudioUrl(segment);
                    if (audioUrl == null || audioUrl.isBlank()) {
                        streamPublisher.sendAudioSkip(
                                context.getEmitter(), context.getTaskId(), context.getConversationId(),
                                seq, segment, "empty-audio-url");
                    } else {
                        streamPublisher.sendAudio(
                                context.getEmitter(), context.getTaskId(), context.getConversationId(),
                                seq, segment, audioUrl);
                    }
                } catch (Exception e) {
                    log.warn("TTS 合成失败 (seq={}): {}", seq, e.getMessage());
                    streamPublisher.sendAudioSkip(
                            context.getEmitter(), context.getTaskId(), context.getConversationId(),
                            seq, segment, e.getClass().getSimpleName());
                }
            }, agentExecutor);

            futures.add(future);
        }

        // 等待所有 TTS 调用完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.warn("并行 TTS 等待异常: {}", e.getMessage());
        }
    }
}

