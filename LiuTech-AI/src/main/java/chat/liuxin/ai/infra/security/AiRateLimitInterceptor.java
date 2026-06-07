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

        synchronized boolean expired(long now, long ttlMillis) {
            return now - lastSeenMillis > ttlMillis;
        }
    }

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
