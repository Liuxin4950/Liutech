package chat.liuxin.ai.agent.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPlanServiceTest {

    private final AgentPlanService planService = new AgentPlanService();

    @Test
    void shouldKeepWritingAssistSeparateFromDraftCreation() {
        var writePlan = planService.buildPlan(AgentIntent.WRITE_ARTICLE, true);
        var draftPlan = planService.buildPlan(AgentIntent.CREATE_DRAFT, true);

        assertEquals("context", writePlan.get(1).getKey());
        assertEquals("taxonomy", writePlan.get(2).getKey());
        assertEquals("confirm", draftPlan.get(2).getKey());
    }
}
