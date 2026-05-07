package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.request.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.agent.response.AvatarCuePayload;
import chat.liuxin.ai.client.TtsClient;
import chat.liuxin.ai.tts.AvatarCueService;
import chat.liuxin.ai.tts.TtsSegmenter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 语音和表情事件编排。
 *
 * 职责：
 * - 将 AI 回复文本分段
 * - 为每段生成表情提示（avatar-cue）
 * - 为每段生成 TTS 音频（audio / audio-skip）
 * - 发送 audio-complete 结束标记
 *
 * 事件顺序：每段先发 avatar-cue 再发 audio，全部发完后发 audio-complete。
 * avatar-cue 与 TTS 开关解耦，TTS 关闭时仍发送表情。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechOrchestrationService {

    private final TtsSegmenter ttsSegmenter;
    private final AvatarCueService avatarCueService;
    private final TtsClient ttsClient;
    private final AgentStreamPublisher streamPublisher;

    /**
     * 发布语音和表情事件。
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

        // 按标点符号切句
        List<String> segments = ttsSegmenter.splitAll(message);
        if (segments.isEmpty() && ttsSegmenter.containsSpeakableText(message)) {
            segments = List.of(message.trim());
        }

        boolean ttsEnabled = Boolean.TRUE.equals(request.getTtsEnabled());
        int seq = 0;
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) continue;
            seq++;

            // 生成并发送表情提示（独立于 TTS）
            AvatarCuePayload cue = avatarCueService.fromText(seq, context.getConversationId(), segment);
            streamPublisher.sendAvatarCue(context.getEmitter(), context.getTaskId(), context.getConversationId(), cue);

            if (!ttsEnabled) continue;

            // 生成并发送音频
            try {
                String audioUrl = ttsClient.inferSingleAudioUrl(segment);
                if (audioUrl == null || audioUrl.isBlank()) {
                    streamPublisher.sendAudioSkip(context.getEmitter(), context.getTaskId(), context.getConversationId(), seq, segment, "empty-audio-url");
                } else {
                    streamPublisher.sendAudio(context.getEmitter(), context.getTaskId(), context.getConversationId(), seq, segment, audioUrl);
                }
            } catch (Exception e) {
                streamPublisher.sendAudioSkip(context.getEmitter(), context.getTaskId(), context.getConversationId(), seq, segment, e.getClass().getSimpleName());
            }
        }

        if (ttsEnabled) {
            streamPublisher.sendAudioComplete(context.getEmitter(), context.getTaskId(), context.getConversationId(), seq, false);
        }
    }
}
