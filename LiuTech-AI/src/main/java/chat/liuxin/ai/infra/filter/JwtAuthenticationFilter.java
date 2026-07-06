package chat.liuxin.ai.infra.filter;

import chat.liuxin.ai.common.utils.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;

/**
 * JWT认证过滤器
 * 自动验证请求中的JWT token，并将用户信息与权限注入到Spring Security上下文
 * token 签名由本服务校验，账号当前状态和角色由主后端 /user/current 进行权威校验
 *
 * 作者：刘鑫，时间：2025-08-26（Asia/Shanghai）
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${blog.api.url:http://backend:8080}")
    private String blogApiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 使用RequestAttributeSecurityContextRepository在请求属性中保存安全上下文
    // 解决SSE流完成后认证上下文丢失问题
    private final SecurityContextRepository securityContextRepository = 
            new RequestAttributeSecurityContextRepository();

    public JwtAuthenticationFilter(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${spring.ai.security.auth-connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${spring.ai.security.auth-read-timeout-ms:5000}") long readTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    /**
     * 每个请求的过滤入口：提取 token 并验证；异常时静默继续，不阻断请求。
     *
     * 认证/授权决定由后续 Spring Security 授权层做出。这样公开端点带无效 token
     * 也能正常访问，认证端点由 authenticationEntryPoint 统一返回 401 JSON。
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            log.info("处理请求: {} {}", request.getMethod(), request.getRequestURI());

            String token = extractTokenFromRequest(request);
            if (token != null) {
                processValidToken(token, request, response);
            }
        } catch (Exception e) {
            log.error("JWT认证过程中发生错误，请求路径: {}, 错误: {}", request.getRequestURI(), e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    /** 从 Authorization: Bearer xxx 头里提取 token；缺失或格式不对返回 null */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    /**
     * 处理有效的JWT token
     *
     * 设计要点：
     * - 无效 token 静默跳过（不在此处 flush 401），由 Spring Security 授权层决定：
     *   公开端点 permitAll 放行，认证端点由 authenticationEntryPoint 返回 401。
     *   避免公开端点带无效 token 时响应被双写损坏。
     * - 用户状态校验结果短期缓存，避免每请求都跨服务 HTTP 调用主后端。
     */
    private void processValidToken(String token, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!jwtUtil.validateToken(token)) {
            log.debug("无效的JWT token，请求路径: {}", request.getRequestURI());
            return;
        }

        String username = jwtUtil.getUsernameFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);

        if (username != null && userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            CurrentUser currentUser = loadCurrentUserFromBlogApi(token, userId);
            if (currentUser == null || !userId.equals(currentUser.userId()) || !username.equals(currentUser.username())) {
                log.warn("AI服务JWT用户状态校验失败，用户ID: {}, 请求路径: {}", userId, request.getRequestURI());
                // 不在此处 flush 401，交由授权层处理，避免公开端点响应损坏
                return;
            }
            setAuthenticationContext(currentUser.username(), currentUser.userId(), currentUser.role(), request, response);
        }
    }

    /** 用户状态缓存 TTL（毫秒）：封禁等状态变更最多延迟此时间生效 */
    private static final long USER_STATUS_CACHE_TTL_MS = 60_000L;
    private final java.util.concurrent.ConcurrentHashMap<Long, CachedUser> userStatusCache = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedUser(CurrentUser user, long cachedAt) {}

    /** 带缓存的用户状态查询：命中且未过期直接返回，否则跨服务拉取并缓存 */
    private CurrentUser loadCurrentUserFromBlogApi(String token, Long userId) {
        CachedUser cached = userStatusCache.get(userId);
        if (cached != null && System.currentTimeMillis() - cached.cachedAt() < USER_STATUS_CACHE_TTL_MS) {
            return cached.user();
        }
        CurrentUser current = fetchCurrentUserFromBlogApi(token);
        if (current != null) {
            userStatusCache.put(userId, new CachedUser(current, System.currentTimeMillis()));
        }
        return current;
    }

    /** 实际发起对主后端 /user/current 的 HTTP 调用，解析 JSON 响应到 CurrentUser；任何异常返回 null */
    private CurrentUser fetchCurrentUserFromBlogApi(String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            ResponseEntity<String> response = restTemplate.exchange(
                    blogApiUrl + "/user/current",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.has("code") || root.get("code").asInt() != 200 || !root.has("data") || root.get("data").isNull()) {
                return null;
            }

            JsonNode data = root.get("data");
            Long currentUserId = data.has("id") && !data.get("id").isNull() ? data.get("id").asLong() : null;
            String currentUsername = data.has("username") && !data.get("username").isNull() ? data.get("username").asText() : null;
            String currentRole = data.has("role") && !data.get("role").isNull() ? data.get("role").asText() : "user";
            return new CurrentUser(currentUserId, currentUsername, currentRole);
        } catch (Exception e) {
            log.warn("调用主后端校验JWT用户状态失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 组装 Spring Security 认证上下文并挂到 SecurityContextHolder。
     *
     * 用户 ID 通过 authToken.details 传递给业务层（{@link chat.liuxin.ai.common.utils.AuthUtils#getCurrentUserId()} 读取）。
     * 额外通过 RequestAttributeSecurityContextRepository 把上下文存到请求属性，
     * 解决 SSE 流式响应完成后 SecurityContextHolder 已清空的问题。
     */
    private void setAuthenticationContext(String username, Long userId, String role, HttpServletRequest request, HttpServletResponse response) {
        Collection<GrantedAuthority> authorities = buildUserAuthorities(role);

        // 构建认证对象（使用带权限的构造函数，自动设置为已认证状态）
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                username, null, authorities);

        // 设置用户详情（将用户ID存储在details中，供控制器使用）
        authToken.setDetails(userId);

        log.info("认证对象创建完成，认证状态: {}", authToken.isAuthenticated());

        // 设置到Spring Security上下文
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);

        // 将安全上下文保存到请求属性中，解决SSE流完成后认证上下文丢失问题
        securityContextRepository.saveContext(context, request, response);

        log.info("JWT认证成功，用户: {}, 角色: {}, 用户ID: {}", username, authorities, userId);
    }
    
    /**
     * 按角色映射到 Spring Security 权限：所有认证用户都有 ROLE_USER；
     * 角色为 admin 时额外授予 ROLE_ADMIN（供 @PreAuthorize("hasRole('ADMIN')") 使用）。
     */
    private Collection<GrantedAuthority> buildUserAuthorities(String role) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        // 默认 ROLE_USER
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        // 如果角色是 admin，添加 ROLE_ADMIN
        if ("admin".equalsIgnoreCase(role)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return authorities;
    }

    private record CurrentUser(Long userId, String username, String role) {
    }
}
