package chat.liuxin.ai.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final ConcurrentHashMap<String, AiRateLimitBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis = new AtomicLong(0);

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

        AiRateLimitBucket bucket = buckets.computeIfAbsent(key, ignored -> new AiRateLimitBucket(now));
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            Object details = authentication.getDetails();
            if (details instanceof Long id) {
                return "user:" + id;
            }
            Object principal = authentication.getPrincipal();
            if (principal != null) {
                return "principal:" + principal;
            }
        }
        return "ip:" + role + ":" + resolveClientIp(request);
    }

    private String resolveRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return "guest";
        }
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return admin ? "admin" : "user";
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
