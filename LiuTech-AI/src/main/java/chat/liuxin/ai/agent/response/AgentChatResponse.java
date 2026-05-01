package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentChatResponse {
    private Boolean success;
    private String message;
    private Long taskId;
    private Long conversationId;
    private String intent;
    private List<AgentPlanStep> plan;
    private ArticleResultsPayload articleResults;
    private ConfirmationRequiredPayload confirmation;
    private String role;
    private Boolean authenticated;
    private Boolean admin;
    private List<String> capabilities;

    public static AgentChatResponse error(String message) {
        return AgentChatResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
