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
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            // 从请求头获取Authorization字段
            String authHeader = request.getHeader("Authorization");
            // 检查Authorization头是否存在且以"Bearer "开头
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                // 提取token字符串(去除"Bearer "前缀)
                String token = authHeader.substring(7);
                // 验证token是否有效
                if (jwtUtil.validateToken(token)) {
                    // 从token中提取用户名和用户ID
                    String username = jwtUtil.getUsernameFromToken(token);
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    // 检查用户名是否存在且当前上下文中没有认证信息
                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        // 创建权限集合
                        Collection<GrantedAuthority> authorities = new ArrayList<>();
                        // 为所有用户添加基本用户角色
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                        // 检查是否为管理员用户，是则添加管理员角色
                        if (isAdminUser(username)) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        }
                        // 创建认证token对象
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                username, null, authorities);
                        // 仅设置userId到details，供后续读取
                        authToken.setDetails(userId);
                        // 将认证信息设置到安全上下文中
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        // 记录认证成功日志
                        log.debug("JWT认证成功，用户: {}, 角色: {}", username, authorities);
                    }
                } else {
                    // 记录无效token警告日志
                    log.warn("无效的JWT token");
                }
            }
        } catch (Exception e) {
            log.error("JWT认证过程中发生错误: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAdminUser(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        String u = username.trim().toLowerCase();
        return "admin".equals(u) || "administrator".equals(u);
    }
}