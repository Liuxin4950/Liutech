package chat.liuxin.ai.agent.application;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AgentUserContextResolver {

    public AgentUserContext resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());

        Long userId = null;
        String username = null;
        boolean admin = false;
        if (authenticated) {
            Object details = authentication.getDetails();
            if (details instanceof Long id) {
                userId = id;
            }
            Object principal = authentication.getPrincipal();
            username = principal == null ? null : String.valueOf(principal);
            admin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_ADMIN"::equals);
        }

        return AgentUserContext.builder()
                .authenticated(authenticated)
                .admin(admin)
                .userId(userId)
                .username(username)
                .bearerToken(extractBearerToken(request))
                .build();
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
