package chat.liuxin.ai.security;

import chat.liuxin.ai.agent.application.AgentUserContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiCapabilityResolver {

    public AiCapabilityContext resolve(AgentUserContext user) {
        boolean authenticated = user != null && user.isAuthenticated();
        boolean admin = authenticated && user.isAdmin();
        return buildContext(
                authenticated,
                admin,
                user == null ? null : user.getUserId(),
                user == null ? null : user.getUsername());
    }

    public AiCapabilityContext resolveCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
        boolean admin = authenticated && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        Long userId = null;
        String username = null;
        if (authenticated) {
            Object details = authentication.getDetails();
            if (details instanceof Long id) {
                userId = id;
            }
            Object principal = authentication.getPrincipal();
            username = principal == null ? null : String.valueOf(principal);
        }
        return buildContext(authenticated, admin, userId, username);
    }

    private AiCapabilityContext buildContext(boolean authenticated, boolean admin, Long userId, String username) {
        List<AiCapability> capabilities = new ArrayList<>();
        capabilities.add(AiCapability.CHAT);
        capabilities.add(AiCapability.READ_PUBLIC_ARTICLES);
        capabilities.add(AiCapability.SUMMARIZE_PUBLIC_ARTICLE);
        capabilities.add(AiCapability.SEARCH_PUBLIC_ARTICLES);
        capabilities.add(AiCapability.RECOMMEND_PUBLIC_ARTICLES);
        if (admin) {
            capabilities.add(AiCapability.WRITE_DRAFT);
            capabilities.add(AiCapability.CREATE_DRAFT_WITH_CONFIRMATION);
            capabilities.add(AiCapability.PUBLISH_POST_WITH_CONFIRMATION);
            capabilities.add(AiCapability.OFFLINE_POST_WITH_CONFIRMATION);
        }
        return AiCapabilityContext.builder()
                .role(!authenticated ? "guest" : admin ? "admin" : "user")
                .authenticated(authenticated)
                .admin(admin)
                .userId(userId)
                .username(username)
                .capabilities(capabilities.stream().map(Enum::name).toList())
                .build();
    }
}
