package chat.liuxin.ai.filter;

import chat.liuxin.ai.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * JWT认证过滤器
 * 自动验证请求中的JWT token，并将用户信息与权限注入到Spring Security上下文
 * 注意：本项目 Users 实体没有角色字段，这里采用"最小可行策略"——
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
    
    // 使用RequestAttributeSecurityContextRepository在请求属性中保存安全上下文
    // 解决SSE流完成后认证上下文丢失问题
    private final SecurityContextRepository securityContextRepository = 
            new RequestAttributeSecurityContextRepository();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
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
    private void processValidToken(String token, HttpServletRequest request, HttpServletResponse response) {
        if (!jwtUtil.validateToken(token)) {
            log.warn("无效的JWT token，请求路径: {}", request.getRequestURI());
            return;
        }
        
        String username = jwtUtil.getUsernameFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);
        
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            setAuthenticationContext(username, userId, request, response);
        }
    }
    
    /**
     * 设置Spring Security认证上下文
     * @param username 用户名
     * @param userId 用户ID
     * @param request HTTP请求
     * @param response HTTP响应
     */
    private void setAuthenticationContext(String username, Long userId, HttpServletRequest request, HttpServletResponse response) {
        Collection<GrantedAuthority> authorities = buildUserAuthorities(username);
        
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