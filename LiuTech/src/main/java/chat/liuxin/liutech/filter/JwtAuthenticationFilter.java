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

    // 依赖说明：
    // - 由 SecurityConfig 将本过滤器注册到过滤器链（位于 UsernamePasswordAuthenticationFilter 之前）
    // - 依赖 JwtUtil 进行 token 的解析与校验
    // - 利用 SecurityContextHolder 注入 Authentication，供 @PreAuthorize 等授权注解使用
    // - 将 userId 写入 Authentication.details，供 UserUtils、MyMetaObjectHandler 等读取
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
        // 白名单：公开接口与跨域预检请求不做认证
        // 说明：不改变逻辑，仅补充注释，便于维护
        // 跳过登录注册接口
        if ("/user/login".equals(requestURI) || "/user/register".equals(requestURI)) {
            return true;
        }
        // 跳过OPTIONS预检请求（避免跨域失败）
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        // 跳过根路径
        if ("/".equals(requestURI)) {
            return true;
        }
        // 跳过静态资源目录
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
        // 构建权限集合：默认 ROLE_USER；admin/administrator 给予 ROLE_ADMIN（最小可行方案）
        Collection<GrantedAuthority> authorities = buildUserAuthorities(username);
        // 使用 UsernamePasswordAuthenticationToken 注入认证主体
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                username, null, authorities);
        // 将 userId 放入 details，供后续业务组件读取（UserUtils、填充审计字段等）
        authToken.setDetails(userId);
        // 注入到安全上下文，授权注解将基于此判断权限
        SecurityContextHolder.getContext().setAuthentication(authToken);
        // 记录认证成功日志，包含用户名与角色
        log.info("JWT认证成功，用户: {}, 角色: {}, 请求路径: {}", username, authorities, request.getRequestURI());
    }
    
    /**
     * 构建用户权限集合
     * @param username 用户名
     * @return 权限集合
     */
    private Collection<GrantedAuthority> buildUserAuthorities(String username) {
        // 说明：当前项目暂无角色表，采用用户名匹配实现管理员权限
        // 后续可替换为：登录时从数据库加载角色并写入JWT的 roles 声明
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (isAdminUser(username)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return authorities;
    }

    private boolean isAdminUser(String username) {
        // 最小实现：基于用户名判断管理员身份；注意仅为权宜之计
        // 推荐后续改为 RBAC（基于数据库角色或JWT roles）
        if (!StringUtils.hasText(username)) {
            return false;
        }
        String u = username.trim().toLowerCase();
        return "admin".equals(u) || "administrator".equals(u);
    }
}