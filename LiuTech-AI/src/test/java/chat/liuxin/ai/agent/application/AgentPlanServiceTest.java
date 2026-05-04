package chat.liuxin.ai.agent.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPlanServiceTest {

    private final AgentPlanService planService = new AgentPlanService();

    @Test
    void shouldKeepWritingAssistSeparateFromDraftCreation() {
        var writePlan = planService.buildPlan(AgentIntent.WRITE_ARTICLE, true);
        var draftPlan = planService.buildPlan(AgentIntent.CREATE_DRAFT, true);

        assertEquals("assist", writePlan.get(1).getKey());
        assertEquals("confirm", draftPlan.get(1).getKey());
    }
}
