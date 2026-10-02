package chat.liuxin.liutech.config;

import java.util.List;

/**
 * Security 白名单常量
 * SecurityConfig 统一管理公开访问权限；JWT 过滤器独立识别请求中的可选登录身份。
 *
 * @author 刘鑫
 */
public final class SecurityWhitelist {

    private SecurityWhitelist() {}

    // ==================== 数据定义 ====================

    /**
     * 完全公开的路径（任意 HTTP 方法均放行）
     */
    public static final List<String> FULLY_PUBLIC = List.of(
            "/",
            "/user/register",
            "/user/login",
            "/user/forgot-password",
            "/user/reset-password",
            "/user/login/email/send",
            "/user/login/email/verify",
            "/user/register/send-code"
    );

    /**
     * GET 方法公开的路径前缀
     * 注意：/posts/** 下的 /posts/my、/posts/drafts、/posts/favorites 需认证，已列入 AUTHENTICATED_PATHS
     */
    public static final List<String> PUBLIC_GET_PREFIXES = List.of(
            "/posts/",
            "/categories/",
            "/series/",
            "/tags/",
            "/comments/",
            "/messages/",
            "/announcements/",
            "/uploads/images/",
            "/uploads/documents/",
            "/uploads/music/",
            "/music/",
            "/sitemap/"
    );

    /**
     * GET 方法公开的精确路径
     */
    public static final List<String> PUBLIC_GET_EXACT = List.of(
            "/carousels",
            "/about",
            "/user/author/profile",
            "/author/profile",
            "/sitemap.xml",
            // 健康检查端点：供 docker compose healthcheck 探针访问，无需登录
            "/actuator/health"
    );

    /**
     * HEAD 方法公开的路径前缀
     */
    public static final List<String> PUBLIC_HEAD_PREFIXES = List.of(
            "/uploads/images/",
            "/uploads/documents/",
            "/uploads/music/"
    );

    /**
     * POST 方法公开的精确路径
     */
    public static final List<String> PUBLIC_POST_EXACT = List.of(
            "/messages"
    );

    /**
     * 拒绝所有访问的路径前缀（GET/HEAD）
     */
    public static final List<String> DENY_PREFIXES = List.of(
            "/resources/",
            "/uploads/resources/"
    );

    /**
     * 虽然路径前缀在 PUBLIC_GET_PREFIXES 中匹配，但需要认证的精确路径
     */
    public static final List<String> AUTHENTICATED_PATHS = List.of(
            "/posts/my",
            "/posts/drafts",
            "/posts/favorites",
            "/posts/view-history",
            "/posts/recommendations"
    );

}
