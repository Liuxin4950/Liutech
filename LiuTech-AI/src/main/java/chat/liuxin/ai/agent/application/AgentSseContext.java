package chat.liuxin.ai.agent.application;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent SSE 执行上下文。
 *
 * <p>持有 SSE 发送所需的上下文信息，包括 emitter 引用、任务 ID 等。
 *
 * @author liuxin
 */
@Data
@Builder
public class AgentSseContext {

    /** SSE 发射器。 */
    private SseEmitter emitter;

    /** 任务 ID（关联同一轮对话的所有事件）。 */
    private Long taskId;

    /** 对话 ID（多轮上下文追踪）。 */
    private Long conversationId;

    /**
     * 创建 SSE 上下文。
     *
     * @param emitter         SSE 发射器
     * @param taskId          任务 ID
     * @param conversationId  对话 ID
     * @return SSE 上下文
     */
    public static AgentSseContext of(SseEmitter emitter, Long taskId, Long conversationId) {
        return AgentSseContext.builder()
                .emitter(emitter)
                .taskId(taskId)
                .conversationId(conversationId)
                .build();
    }
}
