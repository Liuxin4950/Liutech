package chat.liuxin.ai.security;

import chat.liuxin.ai.agent.application.AgentUserContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCapabilityResolverTest {

    private final AiCapabilityResolver resolver = new AiCapabilityResolver();

    @Test
    void shouldExposeOnlyReadCapabilitiesForGuest() {
        AiCapabilityContext context = resolver.resolve(null);

        assertFalse(context.isAuthenticated());
        assertFalse(context.isAdmin());
        assertTrue(context.getCapabilities().contains("CHAT"));
        assertTrue(context.getCapabilities().contains("SEARCH_PUBLIC_ARTICLES"));
        assertFalse(context.getCapabilities().contains("PUBLISH_POST_WITH_CONFIRMATION"));
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
        assertTrue(context.getCapabilities().contains("CREATE_DRAFT_WITH_CONFIRMATION"));
        assertTrue(context.getCapabilities().contains("PUBLISH_POST_WITH_CONFIRMATION"));
        assertTrue(context.getCapabilities().contains("OFFLINE_POST_WITH_CONFIRMATION"));
    }
}
