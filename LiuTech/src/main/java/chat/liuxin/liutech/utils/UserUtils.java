package chat.liuxin.liutech.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 用户工具类
 * 提供用户相关的公共方法，包括获取当前登录用户信息等。
 *
 * 内部使用 Caffeine 缓存用户信息，避免每次请求都查库。
 *
 * @author 刘鑫
 * @date 2025-08-30
 */
@Slf4j
@Component
public class UserUtils {

    @Autowired
    @Lazy
    private UserMapper userMapper;

    /**
     * 用户信息缓存（username → Users），5 分钟自动过期，最多 200 条
     */
    private final Cache<String, Users> userCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(200)
            .build();

    /**
     * 从 Spring Security 上下文获取当前用户 ID。
     *
     * 优先策略：
     * 1) 若 Authentication details 中有 userId（JwtAuthenticationFilter 设置），直接取用；
     * 2) 若 principal 是用户名，从 Caffeine 缓存或数据库查找。
     */
    public Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.debug("Authentication为空或未认证");
                return null;
            }
            if ("anonymousUser".equals(authentication.getPrincipal())) {
                log.debug("当前为匿名用户");
                return null;
            }

            // 1) 尝试从 details 中读取（JwtAuthenticationFilter 已写入 userId）
            if (authentication instanceof UsernamePasswordAuthenticationToken token) {
                Object details = token.getDetails();
                if (details instanceof Long userId) {
                    log.debug("从Authentication details获取到用户ID: {}", userId);
                    return userId;
                }
            }

            // 2) 回退：从 principal 的用户名查找
            Object principal = authentication.getPrincipal();
            if (principal instanceof String username && StringUtils.hasText(username)) {
                Users user = getOrLoadUser(username);
                if (user != null && user.getId() != null) {
                    return user.getId();
                }
                log.warn("未找到用户名为 {} 的用户", username);
            }
        } catch (Exception e) {
            log.error("获取当前用户ID时发生错误", e);
        }
        return null;
    }

    /**
     * 获取当前登录用户的用户名
     */
    public String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.debug("Authentication为空或未认证");
                return null;
            }
            if ("anonymousUser".equals(authentication.getPrincipal())) {
                log.debug("当前为匿名用户");
                return null;
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof String username && StringUtils.hasText(username)) {
                log.debug("获取到当前用户名: {}", username);
                return username;
            }
        } catch (Exception e) {
            log.error("获取当前用户名时发生错误", e);
        }
        return null;
    }

    /**
     * 获取当前登录用户的完整信息（带缓存）
     */
    public Users getCurrentUser() {
        try {
            String username = getCurrentUsername();
            if (!StringUtils.hasText(username)) {
                return null;
            }
            Users user = getOrLoadUser(username);
            if (user == null) {
                log.warn("未找到用户名为 {} 的用户信息", username);
            }
            return user;
        } catch (Exception e) {
            log.error("获取当前用户信息时发生错误", e);
        }
        return null;
    }

    /**
     * 检查当前用户是否已登录
     */
    public boolean isCurrentUserLoggedIn() {
        return getCurrentUserId() != null;
    }

    /**
     * 检查当前用户是否为指定用户
     */
    public boolean isCurrentUser(Long userId) {
        if (userId == null) {
            return false;
        }
        return userId.equals(getCurrentUserId());
    }

    /**
     * 清除指定用户的缓存（用户信息更新后调用）
     */
    public void clearUserCache(String username) {
        if (StringUtils.hasText(username)) {
            userCache.invalidate(username);
            log.debug("已清除用户 {} 的缓存", username);
        }
    }

    /**
     * 清除所有用户缓存
     */
    public void clearAllUserCache() {
        long size = userCache.estimatedSize();
        userCache.invalidateAll();
        log.info("已清除所有用户缓存，共 {} 项", size);
    }

    /**
     * 从缓存获取用户，未命中则查库并回填
     */
    private Users getOrLoadUser(String username) {
        Users cached = userCache.getIfPresent(username);
        if (cached != null) {
            log.debug("从缓存获取到用户信息 for username: {}", username);
            return cached;
        }

        Users user = userMapper.findByUserName(username).stream().findFirst().orElse(null);
        if (user != null) {
            userCache.put(username, user);
            log.debug("从数据库查询并缓存用户信息 for username: {}", username);
        }
        return user;
    }
}
