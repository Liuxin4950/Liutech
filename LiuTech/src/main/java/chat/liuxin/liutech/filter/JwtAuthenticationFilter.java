package chat.liuxin.liutech.filter;

import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
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
 * 角色和账号状态以数据库当前值为准，避免旧 token 在降权或禁用后继续拥有权限
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

    @Autowired
    private UserMapper userMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestURI = request.getRequestURI();
            String method = request.getMethod();
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
        // 与 SecurityConfig permitAll() 保持同步

        // 跳过OPTIONS预检请求（避免跨域失败）
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        // 跳过根路径
        if ("/".equals(requestURI)) {
            return true;
        }
        // 跳过登录注册接口
        if ("/user/login".equals(requestURI) || "/user/register".equals(requestURI)) {
            return true;
        }

        // GET 请求的公开接口（与 SecurityConfig 的 GET permitAll 同步）
        if ("GET".equalsIgnoreCase(method)) {
            // 文章公开接口（不含 /posts/my、/posts/drafts、/posts/favorites 等需认证的路径）
            if ("/posts".equals(requestURI) || "/posts/".equals(requestURI)
                    || requestURI.startsWith("/posts/slug/")
                    || requestURI.startsWith("/posts/id/")
                    || requestURI.startsWith("/posts/category/")
                    || requestURI.startsWith("/posts/tag/")
                    || requestURI.startsWith("/posts/archive/")) {
                return true;
            }
            // 分类、标签、评论、留言、公告、轮播图、作者资料
            if (requestURI.startsWith("/categories/")
                    || requestURI.startsWith("/tags/")
                    || requestURI.startsWith("/comments/")
                    || requestURI.startsWith("/messages/")
                    || requestURI.startsWith("/announcements/")
                    || "/carousels".equals(requestURI)
                    || "/user/author/profile".equals(requestURI)
                    || "/author/profile".equals(requestURI)) {
                return true;
            }
            // TTS 状态与音频、音乐、Sitemap、运行时信息
            if ("/tts/status".equals(requestURI)
                    || requestURI.startsWith("/tts/audio/")
                    || requestURI.startsWith("/music/")
                    || "/sitemap.xml".equals(requestURI)
                    || requestURI.startsWith("/sitemap/")
                    || "/runtime/ai".equals(requestURI)) {
                return true;
            }
            // 公开静态资源目录；付费资源目录 /uploads/resources/** 由受控下载接口处理
            if (requestURI.startsWith("/uploads/images/")
                    || requestURI.startsWith("/uploads/documents/")
                    || requestURI.startsWith("/uploads/music/")
                    || requestURI.startsWith("/files/")) {
                return true;
            }
        }

        // HEAD 请求的公开接口（TTS 音频、上传文件的 HEAD 探测）
        if ("HEAD".equalsIgnoreCase(method)) {
            if (requestURI.startsWith("/tts/audio/")
                    || requestURI.startsWith("/uploads/images/")
                    || requestURI.startsWith("/uploads/documents/")
                    || requestURI.startsWith("/uploads/music/")) {
                return true;
            }
        }

        // POST 请求的公开接口
        if ("POST".equalsIgnoreCase(method)) {
            // POST /messages（匿名留言）
            if ("/messages".equals(requestURI)) {
                return true;
            }
            // POST /tts/speech（TTS 语音合成）
            if ("/tts/speech".equals(requestURI)) {
                return true;
            }
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

        if (username != null && userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Users currentUser = userMapper.selectById(userId);
            if (!isCurrentUserTokenValid(currentUser, username, token)) {
                log.warn("JWT用户状态校验失败，用户ID: {}, 请求路径: {}", userId, request.getRequestURI());
                return;
            }
            setAuthenticationContext(currentUser.getUsername(), userId, currentUser.getRole(), request);
        }
    }

    /**
     * 使用数据库当前用户状态校验 token，避免禁用、删除、降权或改密后的旧 token 继续生效。
     */
    private boolean isCurrentUserTokenValid(Users currentUser, String tokenUsername, String token) {
        if (currentUser == null || currentUser.getDeletedAt() != null) {
            return false;
        }
        if (!StringUtils.hasText(currentUser.getUsername()) || !currentUser.getUsername().equals(tokenUsername)) {
            return false;
        }
        if (!Integer.valueOf(1).equals(currentUser.getStatus())) {
            return false;
        }
        String tokenPasswordHash = jwtUtil.getPasswordHashFromToken(token);
        return !StringUtils.hasText(tokenPasswordHash) || tokenPasswordHash.equals(currentUser.getPasswordHash());
    }

    /**
     * 设置Spring Security认证上下文
     * @param username 用户名
     * @param userId 用户ID
     * @param role 用户角色 (user/admin)
     * @param request HTTP请求
     */
    private void setAuthenticationContext(String username, Long userId, String role, HttpServletRequest request) {
        // 构建权限集合：从数据库当前角色读取，旧 token 中的 role 仅用于兼容解析，不用于授权。
        Collection<GrantedAuthority> authorities = buildUserAuthorities(role);
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
}
