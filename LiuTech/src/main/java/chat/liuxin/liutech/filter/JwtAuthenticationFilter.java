package chat.liuxin.liutech.filter;

import chat.liuxin.liutech.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * JWT认证过滤器
 * 自动验证请求中的JWT token，并将用户信息与权限注入到Spring Security上下文
 * 注意：本项目 Users 实体没有角色字段，这里采用“最小可行策略”——
 * - 默认给所有登录用户 ROLE_USER
 * - 通过用户名匹配 admin 列表赋予 ROLE_ADMIN（可后续替换为数据库角色表）
 * 这样可让 @PreAuthorize("hasRole('ADMIN')") 立即生效，后续再演进为真正的RBAC。
 * 
 * 作者：刘鑫，时间：2025-08-26（Asia/Shanghai）
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestURI = request.getRequestURI();
            String method = request.getMethod();
            log.info("处理请求: {} {}", method, requestURI);
            
            // 跳过公开接口，不进行JWT验证
            if (shouldSkipAuthentication(requestURI, method)) {
                log.info("跳过JWT验证的公开接口: {} {}", method, requestURI);
                filterChain.doFilter(request, response);
                return;
            }
            
            String token = extractTokenFromRequest(request);
            if (token != null) {
                processValidToken(token, request);
            }
        } catch (Exception e) {
            log.error("JWT认证过程中发生错误，请求路径: {}, 错误: {}", request.getRequestURI(), e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
    
    /**
     * 判断是否应该跳过JWT认证
     * @param requestURI 请求URI
     * @param method HTTP方法
     * @return 是否跳过认证
     */
    private boolean shouldSkipAuthentication(String requestURI, String method) {
        // 跳过登录注册接口
        if ("/user/login".equals(requestURI) || "/user/register".equals(requestURI)) {
            return true;
        }
        
        // 跳过OPTIONS预检请求
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        
        // 跳过根路径
        if ("/".equals(requestURI)) {
            return true;
        }
        
        // 跳过静态资源
        if (requestURI.startsWith("/uploads/") || requestURI.startsWith("/files/")) {
            return true;
        }
        
        return false;
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
     */
    private void processValidToken(String token, HttpServletRequest request) {
        if (!jwtUtil.validateToken(token)) {
            log.warn("无效的JWT token，请求路径: {}", request.getRequestURI());
            return;
        }
        
        String username = jwtUtil.getUsernameFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);
        
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            setAuthenticationContext(username, userId, request);
        }
    }
    
    /**
     * 设置Spring Security认证上下文
     * @param username 用户名
     * @param userId 用户ID
     * @param request HTTP请求
     */
    private void setAuthenticationContext(String username, Long userId, HttpServletRequest request) {
        Collection<GrantedAuthority> authorities = buildUserAuthorities(username);
        
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                username, null, authorities);
        authToken.setDetails(userId);
        
        SecurityContextHolder.getContext().setAuthentication(authToken);
        log.info("JWT认证成功，用户: {}, 角色: {}, 请求路径: {}", username, authorities, request.getRequestURI());
    }
    
    /**
     * 构建用户权限集合
     * @param username 用户名
     * @return 权限集合
     */
    private Collection<GrantedAuthority> buildUserAuthorities(String username) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        
        if (isAdminUser(username)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        
        return authorities;
    }

    private boolean isAdminUser(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        String u = username.trim().toLowerCase();
        return "admin".equals(u) || "administrator".equals(u);
    }
}