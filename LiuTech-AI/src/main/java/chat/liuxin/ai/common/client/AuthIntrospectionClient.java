package chat.liuxin.ai.common.client;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 通过主服务确认用户当前状态与角色，不在 AI 服务解析 JWT。 */
@Component
public class AuthIntrospectionClient {

    private static final long CACHE_TTL_MS = 60_000L;

    private final BackendApiTransport transport;
    private final ConcurrentHashMap<String, CachedUser> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupAt = new AtomicLong();

    public AuthIntrospectionClient(BackendApiTransport transport) {
        this.transport = transport;
    }

    public AuthenticatedUser introspect(String token) {
        String cacheKey = sha256(token);
        long now = System.currentTimeMillis();
        cleanupExpired(now);
        CachedUser hit = cache.get(cacheKey);
        if (hit != null && now - hit.cachedAt() < CACHE_TTL_MS) return hit.user();
        if (hit != null) cache.remove(cacheKey, hit);

        JsonNode data = transport.extractData(transport.introspect(token));
        if (data == null) {
            throw new BackendApiTransport.InvalidUserTokenException("登录状态无效或已过期");
        }
        Long userId = data.has("userId") && !data.get("userId").isNull() ? data.get("userId").asLong() : null;
        String username = text(data, "username");
        String role = text(data, "role");
        if (userId == null || username == null || role == null) {
            throw new BackendApiTransport.InvalidUserTokenException("身份服务返回的信息不完整");
        }
        AuthenticatedUser user = new AuthenticatedUser(userId, username, role);
        cache.put(cacheKey, new CachedUser(user, now));
        return user;
    }

    private void cleanupExpired(long now) {
        long previous = lastCleanupAt.get();
        if (now - previous < CACHE_TTL_MS || !lastCleanupAt.compareAndSet(previous, now)) return;
        cache.entrySet().removeIf(entry -> now - entry.getValue().cachedAt() >= CACHE_TTL_MS);
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() && !node.get(field).asText().isBlank()
                ? node.get(field).asText() : null;
    }

    private String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record AuthenticatedUser(Long userId, String username, String role) {}
    private record CachedUser(AuthenticatedUser user, long cachedAt) {}
}
