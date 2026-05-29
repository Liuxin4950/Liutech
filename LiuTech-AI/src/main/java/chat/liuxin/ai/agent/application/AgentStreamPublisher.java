package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.response.AgentCompletePayload;
import chat.liuxin.ai.agent.response.AgentErrorPayload;
import chat.liuxin.ai.agent.response.AgentSseEnvelope;
import chat.liuxin.ai.agent.response.AgentStartPayload;
import chat.liuxin.ai.agent.response.AgentToolEventPayload;
import chat.liuxin.ai.dto.AvatarCuePayload;
import chat.liuxin.ai.agent.response.DataPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent SSE 事件发布器。
 *
 * 负责向 SSE 客户端发送事件，支持统一 envelope 包装。
 * 所有 send 操作使用 synchronized(emitter) 保护，确保多线程并发发送安全。
 *
 * 资源管理：发送失败时自动调用 completeWithError 关闭连接。
 *
 * @author liuxin
 * @see AgentSseEnvelope
 */
@Slf4j
@Component
public class AgentStreamPublisher {

    /**
     * 向 SSE 客户端发送带 envelope 的事件。
     * 事件自动包装为统一格式：
     *   {
     *     "contractVersion": 1,
     *     "event": eventName,
     *     "taskId": taskId,
     *     "conversationId": conversationId,
     *     "timestamp": "2026-05-01T15:28:23.029Z",
     *     "payload": payload
     *   }
     *
     * @param emitter         SSE 发射器，非空
     * @param eventName      事件名称，非空
     * @param taskId         任务 ID
     * @param conversationId 对话 ID
     * @param payload        事件负载，可为 null（发送空 payload 的 envelope）
     */
    public void send(SseEmitter emitter, String eventName, Long taskId, Long conversationId, Object payload) {
        if (emitter == null) {
            log.warn("SSE emitter 为空，跳过发送事件: {}", eventName);
            return;
        }
        try {
            AgentSseEnvelope<Object> envelope = AgentSseEnvelope.of(eventName, taskId, conversationId, payload);
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(eventName).data(envelope));
            }
        } catch (IOException e) {
            handleSendError(emitter, eventName, e);
        }
    }

    /**
     * 发送错误事件。
     * 错误事件包装为 AgentErrorPayload，自动发送 error 事件。
     *
     * @param emitter         SSE 发射器，非空
     * @param code           错误码，非空
     * @param message        错误描述
     * @param stage          错误阶段
     * @param taskId         任务 ID
     * @param conversationId 对话 ID
     */
    public void error(SseEmitter emitter, String code, String message, String stage, Long taskId, Long conversationId) {
        AgentErrorPayload errorPayload = AgentErrorPayload.of(code, message, stage);
        send(emitter, "error", taskId, conversationId, errorPayload);
    }

    /**
     * 发送 agent-start 事件。
     * 声明任务、会话、意图、角色、能力。
     *
     * @param emitter         SSE 发射器
     * @param taskId         任务 ID
     * @param conversationId 对话 ID
     * @param handlerName  Handler 名称
     * @param role           角色
     * @param capabilities   能力列表
     */
    public void sendAgentStart(SseEmitter emitter, Long taskId, Long conversationId,
                               String handlerName, String role, java.util.List<String> capabilities) {
        AgentStartPayload payload = AgentStartPayload.builder()
                .taskId(taskId)
                .conversationId(conversationId)
                .handlerName(handlerName)
                .role(role)
                .capabilities(capabilities)
                .build();
        send(emitter, "agent-start", taskId, conversationId, payload);
    }

    /**
     * 发送 agent-plan 事件。
     * 展示用户可理解的计划步骤，不暴露模型内部推理链。
     *
     * @param emitter         SSE 发射器
     * @param taskId         任务 ID
     * @param conversationId 对话 ID
     * @param steps          计划步骤列表
     */
    public void sendAgentPlan(SseEmitter emitter, Long taskId, Long conversationId, java.util.List<?> steps) {
        java.util.Map<String, Object> payload = java.util.Map.of("steps", steps);
        send(emitter, "agent-plan", taskId, conversationId, payload);
    }

    /**
     * 发送 tool-start 事件。
     * 通知前端工具调用开始，可展示"正在查询..."等状态。
     *
     * @param emitter         SSE 发射器
     * @param taskId         任务 ID
     * @param conversationId 对话 ID
     * @param toolName       工具名称，格式：命名空间.方法名
     * @param displayName    展示名称，用于前端展示给用户
     * @param inputSummary   输入摘要，不包含敏感信息
     */
    public void sendToolStart(SseEmitter emitter, Long taskId, Long conversationId,
                              String toolName, String displayName, String inputSummary) {
        AgentToolEventPayload payload = AgentToolEventPayload.start(toolName, displayName, inputSummary);
        send(emitter, "tool-start", taskId, conversationId, payload);
        log.info("taskId={} tool={} start input={}", taskId, toolName, inputSummary);
    }

    /**
     * 发送 tool-result 成功事件。
     * 通知前端工具调用完成（成功）。
     *
     * @param emitter         SSE 发射器
     * @param taskId         任务 ID
     * @param conversationId 对话 ID
     * @param toolName       工具名称
     * @param displayName    展示名称
     * @param durationMs     执行耗时（毫秒）
     * @param resultSummary  结果摘要，不包含敏感信息
     */
    public void sendToolResultSuccess(SseEmitter emitter, Long taskId, Long conversationId,
                                      String toolName, String displayName, Long durationMs, String resultSummary) {
        AgentToolEventPayload payload = AgentToolEventPayload.success(toolName, displayName, durationMs, resultSummary);
        send(emitter, "tool-result", taskId, conversationId, payload);
        log.info("taskId={} tool={} duration={}ms success=true", taskId, toolName, durationMs);
    }

    /**
     * 发送 tool-result 失败事件。
     * 通知前端工具调用完成（失败）。
     *
     * @param emitter         SSE 发射器
     * @param taskId         任务 ID
     * @param conversationId 对话 ID
     * @param toolName       工具名称
     * @param displayName    展示名称
     * @param durationMs     执行耗时（毫秒）
     * @param errorMessage   错误信息
     */
    public void sendToolResultFailure(SseEmitter emitter, Long taskId, Long conversationId,
                                     String toolName, String displayName, Long durationMs, String errorMessage) {
        AgentToolEventPayload payload = AgentToolEventPayload.failure(toolName, displayName, durationMs, errorMessage);
        send(emitter, "tool-result", taskId, conversationId, payload);
        log.warn("taskId={} tool={} duration={}ms success=false error={}", taskId, toolName, durationMs, errorMessage);
    }

    /**
     * 发送自然语言文本事件。
     * 只承载模型输出的自然语言文本，不承载结构化数据。
     *
     * @param emitter         SSE 发射器
     * @param taskId         任务 ID
     * @param conversationId 对话 ID
     * @param content        文本内容
     */
    public void sendData(SseEmitter emitter, Long taskId, Long conversationId, String content) {
        send(emitter, "data", taskId, conversationId, DataPayload.of(content));
    }

    public void sendAvatarCue(SseEmitter emitter, Long taskId, Long conversationId, AvatarCuePayload payload) {
        send(emitter, "avatar-cue", taskId, conversationId, payload);
    }

    public void sendAudio(SseEmitter emitter, Long taskId, Long conversationId, int seq, String text, String audioUrl) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("seq", seq);
        payload.put("text", text);
        payload.put("audioUrl", audioUrl);
        payload.put("conversationId", conversationId);
        send(emitter, "audio", taskId, conversationId, payload);
    }

    public void sendAudioSkip(SseEmitter emitter, Long taskId, Long conversationId, int seq, String text, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("seq", seq);
        payload.put("text", text);
        payload.put("reason", reason == null ? "unknown" : reason);
        payload.put("conversationId", conversationId);
        send(emitter, "audio-skip", taskId, conversationId, payload);
    }

    public void sendAudioComplete(SseEmitter emitter, Long taskId, Long conversationId, int segments, boolean timedOut) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("segments", segments);
        payload.put("timedOut", timedOut);
        payload.put("conversationId", conversationId);
        send(emitter, "audio-complete", taskId, conversationId, payload);
    }

    /**
     * 发送 complete 事件。
     * 标记 Agent 任务完成。
     *
     * @param emitter         SSE 发射器
     * @param taskId         任务 ID
     * @param conversationId 对话 ID
     */
    public void sendComplete(SseEmitter emitter, Long taskId, Long conversationId) {
        AgentCompletePayload payload = AgentCompletePayload.builder()
                .taskId(taskId)
                .conversationId(conversationId)
                .build();
        send(emitter, "complete", taskId, conversationId, payload);
    }

    /**
     * 处理发送错误。
     * 仅当 emitter 未完成时才调用 completeWithError，避免重复关闭导致 IllegalStateException。
     *
     * @param emitter    SSE 发射器
     * @param eventName 事件名称（用于日志）
     * @param e         IO 异常
     */
    private void handleSendError(SseEmitter emitter, String eventName, IOException e) {
        log.warn("发送 Agent SSE 事件失败: event={}, error={}", eventName, e.getMessage());
        try {
            emitter.completeWithError(e);
        } catch (IllegalStateException ignored) {
            log.debug("SSE emitter 已处于完成状态，跳过 completeWithError");
        }
    }
}


