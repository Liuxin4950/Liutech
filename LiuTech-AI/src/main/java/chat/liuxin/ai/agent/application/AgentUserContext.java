package chat.liuxin.ai.agent.application;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentUserContext {
    private Long userId;
    private String username;
    private boolean authenticated;
    private boolean admin;
    private String bearerToken;

    public String userIdString() {
        return userId == null ? null : userId.toString();
    }
}
