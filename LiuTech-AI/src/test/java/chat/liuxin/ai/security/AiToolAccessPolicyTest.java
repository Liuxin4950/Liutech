package chat.liuxin.ai.security;

import chat.liuxin.ai.agent.application.AgentUserContext;
import chat.liuxin.ai.agent.domain.AgentActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiToolAccessPolicyTest {

    private final AiToolAccessPolicy policy = new AiToolAccessPolicy();

    @Test
    void shouldRejectGuestWriteAction() {
        assertThrows(AiToolAccessDeniedException.class,
                () -> policy.assertAllowed(null, AgentActionType.PUBLISH_POST));
    }

    @Test
    void shouldRejectNormalUserWriteAction() {
        AgentUserContext user = AgentUserContext.builder()
                .authenticated(true)
                .admin(false)
                .userId(2L)
                .username("user")
                .build();

        assertThrows(AiToolAccessDeniedException.class,
                () -> policy.assertAllowed(user, AgentActionType.CREATE_DRAFT));
    }

    @Test
    void shouldAllowAdminWriteAction() {
        AgentUserContext admin = AgentUserContext.builder()
                .authenticated(true)
                .admin(true)
                .userId(1L)
                .username("admin")
                .build();

        assertDoesNotThrow(() -> policy.assertAllowed(admin, AgentActionType.PUBLISH_POST));
    }
}
