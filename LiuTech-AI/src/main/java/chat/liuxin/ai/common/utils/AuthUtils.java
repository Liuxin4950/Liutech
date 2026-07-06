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
     * 读取当前请求线程绑定的用户 ID。
     *
     * JwtAuthenticationFilter 会在鉴权时把 userId 放到 Authentication.details,
     * 这里做类型安全的拆箱,未认证或 details 不是 Long 时返回 null。
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
     * 与 {@link #getCurrentUserId} 相同,但返回字符串形式。
     *
     * 记忆库 MemoryService 等第三方组件要求 userId 为 String,专门提供该便捷方法避免各处重复 toString。
     */
    public String getCurrentUserIdStr() {
        Long id = getCurrentUserId();
        return id != null ? id.toString() : null;
    }

    /**
     * 读取当前用户的用户名(Authentication.principal)。
     *
     * 匿名访问 (anonymousUser) 或 principal 类型异常时返回 null。
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
     * 判断当前用户是否具备 ROLE_ADMIN,用于管理端接口的细粒度权限拦截。
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
     * 把 Spring Security 的权限集合折叠为业务侧使用的三态字符串:
     * "admin" / "user" / "guest",便于在提示词、日志中直接引用。
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
