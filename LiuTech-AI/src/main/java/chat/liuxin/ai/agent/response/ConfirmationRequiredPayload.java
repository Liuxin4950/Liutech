package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfirmationRequiredPayload {
    private Long actionId;
    private String actionType;
    private String title;
    private String description;
    private Object preview;
    private String riskLevel;
}
