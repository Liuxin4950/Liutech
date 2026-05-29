package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.AgentUserContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCapabilityResolverTest {

    private final AiCapabilityResolver resolver = new AiCapabilityResolver();

    @Test
    void shouldExposeReadCapabilitiesForAnonymous() {
        AiCapabilityContext context = resolver.resolve(null);

        assertFalse(context.isAuthenticated());
        assertFalse(context.isAdmin());
        assertTrue(context.getCapabilities().contains("CHAT"));
        assertTrue(context.getCapabilities().contains("READ"));
        assertFalse(context.getCapabilities().contains("WRITE"));
    }

    @Test
    void shouldExposeWriteCapabilitiesForAdmin() {
        AgentUserContext user = AgentUserContext.builder()
                .authenticated(true)
                .admin(true)
                .userId(1L)
                .username("admin")
                .build();

        AiCapabilityContext context = resolver.resolve(user);

        assertTrue(context.isAuthenticated());
        assertTrue(context.isAdmin());
        assertTrue(context.getCapabilities().contains("CHAT"));
        assertTrue(context.getCapabilities().contains("READ"));
        assertTrue(context.getCapabilities().contains("WRITE"));
    }
}

