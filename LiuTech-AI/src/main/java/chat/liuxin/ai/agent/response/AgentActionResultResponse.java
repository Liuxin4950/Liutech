package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentActionResultResponse {
    private Boolean success;
    private String message;
    private Long actionId;
    private String actionType;
    private String status;
    private Object target;
}
