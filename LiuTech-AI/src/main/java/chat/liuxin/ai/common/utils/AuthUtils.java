package chat.liuxin.ai.common.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 认证上下文工具类。
 *
 * 统一从 SecurityContextHolder 中提取用户信息，替代各处手写
 * "authentication.getDetails() instanceof Long" 样板代码。
 *
 * @author 刘鑫
 */
@Component
public class AuthUtils {

    /**
     * 获取当前已认证用户的 ID。
     *
     * @return 用户 ID，未认证或匿名时返回 null
     */
    public Long getCurrentUserId() {
        Authentication auth = getAuthentication();
        if (auth == null) {
            return null;
        }
        Object details = auth.getDetails();
        return details instanceof Long id ? id : null;
    }

    /**
     * 获取当前已认证用户的 ID（字符串形式）。
     * 用于需要 String 类型 userId 的场景（如 MemoryService）。
     *
     * @return 用户 ID 字符串，未认证时返回 null
     */
    public String getCurrentUserIdStr() {
        Long id = getCurrentUserId();
        return id != null ? id.toString() : null;
    }

    /**
     * 获取当前已认证用户的用户名。
     *
     * @return 用户名，未认证或匿名时返回 null
     */
    public String getCurrentUsername() {
        Authentication auth = getAuthentication();
        if (auth == null || !auth.isAuthenticated() || isAnonymous(auth)) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof String username ? username : null;
    }

    /**
     * 判断当前用户是否具有 ADMIN 角色。
     */
    public boolean isAdmin() {
        Authentication auth = getAuthentication();
        if (auth == null || !auth.isAuthenticated() || isAnonymous(auth)) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    /**
     * 解析当前用户角色标识。
     *
     * @return "admin" / "user" / "guest"
     */
    public String resolveRole() {
        Authentication auth = getAuthentication();
        if (auth == null || !auth.isAuthenticated() || isAnonymous(auth)) {
            return "guest";
        }
        boolean admin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return admin ? "admin" : "user";
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean isAnonymous(Authentication auth) {
        return "anonymousUser".equals(auth.getPrincipal());
    }
}
