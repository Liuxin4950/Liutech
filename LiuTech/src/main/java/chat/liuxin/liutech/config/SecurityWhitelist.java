package chat.liuxin.liutech.config;

import java.util.List;

/**
 * Security 白名单常量
 * SecurityConfig 与 JwtAuthenticationFilter 共享同一份白名单，避免双份维护导致不一致。
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
            "/tts/audio/",
            "/music/",
            "/sitemap/"
    );

    /**
     * GET 方法公开的精确路径
     */
    public static final List<String> PUBLIC_GET_EXACT = List.of(
            "/carousels",
            "/user/author/profile",
            "/author/profile",
            "/tts/status",
            "/sitemap.xml",
            "/runtime/ai",
            // 健康检查端点：供 docker compose healthcheck 探针访问，无需登录
            "/actuator/health"
    );

    /**
     * HEAD 方法公开的路径前缀
     */
    public static final List<String> PUBLIC_HEAD_PREFIXES = List.of(
            "/tts/audio/",
            "/uploads/images/",
            "/uploads/documents/",
            "/uploads/music/"
    );

    /**
     * POST 方法公开的精确路径
     */
    public static final List<String> PUBLIC_POST_EXACT = List.of(
            "/messages",
            "/tts/speech"
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
            "/posts/favorites"
    );

    // ==================== 匹配工具方法 ====================

    /**
     * 判断请求是否应跳过 JWT 认证（即公开接口）
     *
     * @param uri    请求 URI
     * @param method HTTP 方法
     * @return true 表示跳过认证
     */
    public static boolean shouldSkipAuthentication(String uri, String method) {
        // OPTIONS 预检请求始终放行
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // 需认证的精确路径，不跳过
        if (AUTHENTICATED_PATHS.contains(uri)) {
            return false;
        }

        // 完全公开路径
        if (FULLY_PUBLIC.contains(uri)) {
            return true;
        }

        if ("GET".equalsIgnoreCase(method)) {
            // 精确匹配
            if (PUBLIC_GET_EXACT.contains(uri)) {
                return true;
            }
            // 前缀匹配
            for (String prefix : PUBLIC_GET_PREFIXES) {
                if (uri.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        if ("HEAD".equalsIgnoreCase(method)) {
            for (String prefix : PUBLIC_HEAD_PREFIXES) {
                if (uri.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        if ("POST".equalsIgnoreCase(method)) {
            return PUBLIC_POST_EXACT.contains(uri);
        }

        return false;
    }
}
