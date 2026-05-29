package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 启动事件负载。
 * 声明任务、会话、意图、角色、能力。
 * JSON 结构：
 *   {
 *     "taskId": 123,
 *     "conversationId": 43,
 *     "handlerName": "search",
 *     "role": "user|admin",
 *     "capabilities": ["CHAT", "READ", "WRITE"]
 *   }
 *
 * @author liuxin
 * @see AgentSseEnvelope
 */
@Data
@Builder
public class AgentStartPayload {

    /** 任务 ID。 */
    private Long taskId;

    /** 对话 ID。 */
    private Long conversationId;

    /**
     * Handler 名称。
     * 例如：search、recommend、identity、chat 等。
     */
    private String handlerName;

    /**
     * 角色。
     * user=普通用户（含访客），admin=管理员。
     */
    private String role;

    /**
     * 支持的能力列表。
     * 例如：["CHAT", "READ", "WRITE"]
     */
    private List<String> capabilities;
}
