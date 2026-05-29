package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.AgentUserContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiToolAccessPolicyTest {

    private final AiToolAccessPolicy policy = new AiToolAccessPolicy();

    @Test
    void shouldRejectGuestWriteAction() {
        assertThrows(AiToolAccessDeniedException.class,
                () -> policy.assertWriteAllowed(null));
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
                () -> policy.assertWriteAllowed(user));
    }

    @Test
    void shouldAllowAdminWriteAction() {
        AgentUserContext admin = AgentUserContext.builder()
                .authenticated(true)
                .admin(true)
                .userId(1L)
                .username("admin")
                .build();

        assertDoesNotThrow(() -> policy.assertWriteAllowed(admin));
    }
}

