package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

/**
 * Agent Action 执行结果负载。
 * 用于 action-result 事件，记录确认操作的执行结果。
 * JSON 结构：
 *   {
 *     "actionId": 456,
 *     "actionType": "CREATE_DRAFT",
 *     "success": true,
 *     "result": { "postId": 789 },
 *     "errorMessage": null
 *   }
 *
 * @author liuxin
 * @see AgentSseEnvelope
 */
@Data
@Builder
public class AgentResultPayload {

    /** Action ID。 */
    private Long actionId;

    /**
     * Action 类型。
     * 例如：CREATE_DRAFT, PUBLISH_POST, OFFLINE_POST
     */
    private String actionType;

    /** 是否成功。 */
    private Boolean success;

    /**
     * 执行结果。
     * 成功时包含具体结果数据。
     */
    private Object result;

    /**
     * 错误信息。
     * 失败时包含错误描述。
     */
    private String errorMessage;
}
