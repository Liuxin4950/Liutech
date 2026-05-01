package chat.liuxin.ai.agent.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlanStep {
    private String key;
    private String title;
    private String status;
}
