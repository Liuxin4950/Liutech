package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 任务完成事件负载。
 * JSON 结构：{ "taskId": 123, "conversationId": 43 }
 *
 * @author liuxin
 * @see AgentSseEnvelope
 */
@Data
@Builder
public class AgentCompletePayload {

    /** 任务 ID。 */
    private Long taskId;

    /** 对话 ID。 */
    private Long conversationId;
}
