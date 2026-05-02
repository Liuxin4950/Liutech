package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.domain.AgentTask;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent SSE 执行上下文。
 *
 * 持有 SSE 发送所需的上下文信息，包括 emitter 引用、任务信息等。
 * 在 executeStream 执行过程中被创建并使用。
 *
 * 设计说明：
 * - taskId 和 conversationId 从 AgentTask 中获取
 * - timestamp 由 AgentSseEnvelope.of() 在每次发送事件时自动生成
 * - emitter 引用用于发送 SSE 事件
 *
 * @author liuxin
 * @see AgentStreamPublisher
 */
@Data
@Builder
public class AgentSseContext {

    /**
     * SSE 发射器。
     * 用于向客户端发送 SSE 事件。
     */
    private SseEmitter emitter;

    /**
     * 任务 ID。
     * 从 AgentTask.getId() 获取，关联同一轮对话的所有事件。
     */
    private Long taskId;

    /**
     * 对话 ID。
     * 用于多轮上下文追踪。
     */
    private Long conversationId;

    /**
     * 创建 SSE 上下文。
     *
     * @param emitter         SSE 发射器，非空
     * @param task           Agent 任务，非空
     * @param conversationId 对话 ID，为 null 时回退到 task.getConversationId()
     * @return SSE 上下文
     */
    public static AgentSseContext of(SseEmitter emitter, AgentTask task, Long conversationId) {
        return AgentSseContext.builder()
                .emitter(emitter)
                .taskId(task.getId())
                .conversationId(conversationId != null ? conversationId : task.getConversationId())
                .build();
    }
}
