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
 *     "intent": "SEARCH_ARTICLES",
 *     "role": "guest|user|admin",
 *     "capabilities": ["CHAT", "SEARCH_PUBLIC_ARTICLES", ...]
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
     * 意图名称。
     * 例如：SEARCH_ARTICLES、RECOMMEND_ARTICLES、IDENTITY、CHAT 等。
     */
    private String intent;

    /**
     * 角色。
     * guest=访客，user=普通用户，admin=管理员。
     */
    private String role;

    /**
     * 支持的能力列表。
     * 例如：["CHAT", "SEARCH_PUBLIC_ARTICLES", "WRITE_DRAFT"]
     */
    private List<String> capabilities;
}
