package chat.liuxin.liutech.filter;

import chat.liuxin.liutech.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT认证过滤器
 * 自动验证请求中的JWT token，无需在Controller中手动验证
 * 
 * @author liuxin
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
            // 获取Authorization头
            String authHeader = request.getHeader("Authorization");
            
            // 检查是否有Bearer token
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                // 提取token
                String token = authHeader.substring(7);
                
                // 验证token
                if (jwtUtil.validateToken(token)) {
                    // 从token中获取用户名
                    String username = jwtUtil.getUsernameFromToken(token);
                    
                    // 如果用户名不为空且当前没有认证信息
                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        // 创建认证对象
                        UsernamePasswordAuthenticationToken authToken = 
                            new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());
                        
                        // 设置详细信息
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        
                        // 设置到安全上下文
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        
                        log.debug("JWT认证成功，用户: {}", username);
                    }
                } else {
                    log.warn("无效的JWT token");
                }
            }
        } catch (Exception e) {
            log.error("JWT认证过程中发生错误: {}", e.getMessage());
        }
        
        // 继续过滤器链
        filterChain.doFilter(request, response);
    }
}