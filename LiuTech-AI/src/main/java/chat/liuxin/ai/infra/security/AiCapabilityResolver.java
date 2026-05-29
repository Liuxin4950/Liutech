package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.AgentUserContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 能力解析器。
 *
 * <p>根据用户角色解析可用的 AI 能力。
 * <ul>
 *   <li>未登录用户 / 普通用户 → CHAT + READ</li>
 *   <li>管理员 → CHAT + READ + WRITE</li>
 * </ul>
 *
 * @author liuxin
 */
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
        capabilities.add(AiCapability.READ);
        if (admin) {
            capabilities.add(AiCapability.WRITE);
        }
        return AiCapabilityContext.builder()
                .role(admin ? "admin" : "user")
                .authenticated(authenticated)
                .admin(admin)
                .userId(userId)
                .username(username)
                .capabilities(capabilities.stream().map(Enum::name).toList())
                .build();
    }
}

