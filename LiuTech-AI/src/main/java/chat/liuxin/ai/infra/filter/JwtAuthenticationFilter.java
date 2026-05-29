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
    
    /**
     * 从请求中提取JWT token
     * @param request HTTP请求
     * @return JWT token字符串，如果不存在则返回null
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    /**
     * 处理有效的JWT token
     * @param token JWT token
     * @param request HTTP请求
     * @param response HTTP响应
     */

    /**
     * 发送 401 未认证响应（JSON 格式）。
     * 当 JWT token 无效或过期时使用，替代静默降级为匿名。
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        // 使用 ObjectMapper 序列化，避免 message 中的特殊字符导致 JSON 注入
        String json = objectMapper.writeValueAsString(
                java.util.Map.of("success", false, "message", message, "code", 401));
        response.getWriter().write(json);
        response.getWriter().flush();
    }
    private void processValidToken(String token, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!jwtUtil.validateToken(token)) {
            log.warn("无效的JWT token，请求路径: {}", request.getRequestURI());
            sendUnauthorized(response, "无效或过期的JWT token");
            return;
        }

        String username = jwtUtil.getUsernameFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);

        if (username != null && userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            CurrentUser currentUser = loadCurrentUserFromBlogApi(token);
            if (currentUser == null || !userId.equals(currentUser.userId()) || !username.equals(currentUser.username())) {
                log.warn("AI服务JWT用户状态校验失败，用户ID: {}, 请求路径: {}", userId, request.getRequestURI());
                sendUnauthorized(response, "用户账号状态异常，请重新登录");
                return;
            }
            setAuthenticationContext(currentUser.username(), currentUser.userId(), currentUser.role(), request, response);
        }
    }

    private CurrentUser loadCurrentUserFromBlogApi(String token) {
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
     * 设置Spring Security认证上下文
     * @param username 用户名
     * @param userId 用户ID
     * @param role 用户角色 (user/admin)
     * @param request HTTP请求
     * @param response HTTP响应
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
     * 构建用户权限集合
     * @param role 用户角色 (user/admin)
     * @return 权限集合
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
