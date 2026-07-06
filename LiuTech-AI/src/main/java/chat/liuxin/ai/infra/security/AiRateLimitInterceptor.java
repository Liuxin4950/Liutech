package chat.liuxin.ai.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import chat.liuxin.ai.common.utils.AuthUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 请求限流拦截器（滑动窗口，按角色分级）。
 *
 * 每个请求按用户身份或客户端 IP 归入一个 Bucket，
 * 在 windowSeconds 时间窗内超过阈值就返回 HTTP 429。
 *
 * 阈值分三档：guest / user / admin，从 {@link AiRequestRateLimitProperties} 读取。
 * 已登录优先按 userId 计数，无 userId 时按 username，都没有则按 IP。
 * 内部定期清理过期的 Bucket 防止内存膨胀。
 */
@Component
@RequiredArgsConstructor
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private final AiRequestRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final AuthUtils authUtils;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis = new AtomicLong(0);

    /** 滑动窗口计数桶，每个 key 一个实例 */
    private static class Bucket {
        private long windowStartMillis;
        private int count;
        private long lastSeenMillis;

        Bucket(long now) {
            this.windowStartMillis = now;
            this.lastSeenMillis = now;
        }

        /**
         * 尝试获取一次请求配额：窗口过期则重置计数，未超阈值则计数 +1 并放行，
         * 已达阈值返回 false（触发 429）。
         */
        synchronized boolean tryAcquire(long now, long windowMillis, int maxRequests) {
            lastSeenMillis = now;
            if (now - windowStartMillis >= windowMillis) {
                windowStartMillis = now;
                count = 0;
            }
            if (count >= maxRequests) {
                return false;
            }
            count++;
            return true;
        }

        /** 长时间没被访问的 Bucket 视为过期，供清理任务回收 */
        synchronized boolean expired(long now, long ttlMillis) {
            return now - lastSeenMillis > ttlMillis;
        }
    }

    /**
     * Spring MVC 拦截入口：预检请求(OPTIONS)和关闭限流时直接放行；
     * 否则按角色查阈值、按 key 找/建 Bucket、扣配额，超限直接写 429 JSON 并中断。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);

        String role = resolveRole();
        String key = resolveKey(request, role);
        int maxRequests = resolveMaxRequests(role);
        long windowMillis = Math.max(1, properties.getWindowSeconds()) * 1000L;

        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now));
        if (bucket.tryAcquire(now, windowMillis, maxRequests)) {
            return true;
        }

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "success", false,
                "code", "RATE_LIMITED",
                "message", "请求过于频繁，请稍后再试"));
        return false;
    }

    /**
     * 惰性清理过期 Bucket。
     *
     * 用 CAS 保证同一时刻只有一个线程执行清理；
     * 超过 maxTrackedKeys 时直接清空 map 兜底防止内存无限增长。
     */
    private void cleanupIfNeeded(long now) {
        long previous = lastCleanupMillis.get();
        long cleanupIntervalMillis = Math.max(30_000L, properties.getWindowSeconds() * 1000L);
        if (now - previous < cleanupIntervalMillis || !lastCleanupMillis.compareAndSet(previous, now)) {
            return;
        }
        long ttlMillis = Math.max(60_000L, properties.getWindowSeconds() * 3000L);
        buckets.entrySet().removeIf(entry -> entry.getValue().expired(now, ttlMillis));
        if (buckets.size() > properties.getMaxTrackedKeys()) {
            buckets.clear();
        }
    }

    /**
     * 构造计数 key：已登录用 userId/username（跨 IP 稳定计数），未登录用 role+IP。
     * role 参与 key 是为了避免同一 IP 在游客/用户切换时共用一个 Bucket。
     */
    private String resolveKey(HttpServletRequest request, String role) {
        Long userId = authUtils.getCurrentUserId();
        if (userId != null) {
            return "user:" + userId;
        }
        String username = authUtils.getCurrentUsername();
        if (username != null) {
            return "principal:" + username;
        }
        return "ip:" + role + ":" + resolveClientIp(request);
    }

    private String resolveRole() {
        return authUtils.resolveRole();
    }

    private int resolveMaxRequests(String role) {
        return switch (role) {
            case "admin" -> properties.getAdminMaxRequests();
            case "user" -> properties.getUserMaxRequests();
            default -> properties.getGuestMaxRequests();
        };
    }

    /**
     * 解析真实客户端 IP。
     *
     * Nginx 反代下 X-Forwarded-For 是逗号分隔的链路，第一个是最原始的客户端 IP；
     * 兜底读 X-Real-IP，都没有才用 RemoteAddr（此时通常是 Nginx 容器 IP，不准）。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
