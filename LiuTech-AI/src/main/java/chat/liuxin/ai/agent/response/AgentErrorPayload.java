package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 错误事件负载。
 * 包含稳定错误码和阶段字段，前端按 error 事件类型处理。
 *
 * @author liuxin
 * @see AgentSseEnvelope
 */
@Data
@Builder
public class AgentErrorPayload {

    /**
     * 错误码。
     * 取值见 AgentErrorCode 枚举。
     */
    private String code;

    /**
     * 友好的错误描述。
     * 用于展示给用户。
     */
    private String message;

    /**
     * 错误发生阶段。
     * 取值见 AgentErrorStage 枚举。
     */
    private String stage;

    /**
     * 创建错误负载。
     *
     * @param code    错误码，非空
     * @param message 错误描述，非空
     * @param stage   错误阶段，非空
     */
    public static AgentErrorPayload of(String code, String message, String stage) {
        return AgentErrorPayload.builder()
                .code(code)
                .message(message)
                .stage(stage)
                .build();
    }
}
