package chat.liuxin.liutech.utils;

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

import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户工具类
 * 提供用户相关的公共方法，包括获取当前登录用户信息等
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
     * 缓存过期时间：5 分钟（毫秒）
     */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 用户名到用户ID的缓存，避免重复查询数据库
     */
    private final ConcurrentHashMap<String, Long> usernameToUserIdCache = new ConcurrentHashMap<>();

    /**
     * 用户信息缓存，避免重复查询数据库
     */
    private final ConcurrentHashMap<String, Users> usernameToUserCache = new ConcurrentHashMap<>();

    /**
     * 缓存写入时间戳（用户名 -> 写入时的毫秒数）
     */
    private final ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    /**
     * 判断指定用户名的缓存是否已过期
     *
     * @param username 缓存键
     * @return true 表示已过期或无记录，false 表示未过期
     */
    private boolean isCacheExpired(String username) {
        Long timestamp = cacheTimestamps.get(username);
        return timestamp == null || (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
    }

    /**
     * 清除指定用户名的所有缓存条目（包括时间戳）
     */
    private void evictCache(String username) {
        usernameToUserIdCache.remove(username);
        usernameToUserCache.remove(username);
        cacheTimestamps.remove(username);
        log.debug("缓存已过期，清除用户 {} 的缓存", username);
    }

    /**
     * 从Spring Security上下文获取当前用户ID
     * 优先策略：
     * 1) 若 Authentication 是 UsernamePasswordAuthenticationToken，且 details 中放了 userId（由Jwt过滤器设置），直接取用；
     * 2) 若 principal 是用户名，先从缓存查找，若缓存未命中则查询数据库并缓存；
     * 3) 否则返回 null（调用处可决定是否给默认值）。
     *
     * @return 当前用户ID，如果未登录或获取失败则返回null
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

            // 1) 尝试从details中读取（JwtAuthenticationFilter已写入userId）
            if (authentication instanceof UsernamePasswordAuthenticationToken token) {
                Object details = token.getDetails();
                if (details instanceof Long userId) {
                    log.debug("从Authentication details获取到用户ID: {}", userId);
                    return userId;
                }
            }

            // 2) 回退：从principal的用户名获取用户ID
            Object principal = authentication.getPrincipal();
            if (principal instanceof String username && StringUtils.hasText(username)) {
                // 先从缓存查找（检查 TTL）
                if (!isCacheExpired(username)) {
                    Long cachedUserId = usernameToUserIdCache.get(username);
                    if (cachedUserId != null) {
                        log.debug("从缓存获取到用户ID: {} for username: {}", cachedUserId, username);
                        return cachedUserId;
                    }
                } else {
                    evictCache(username);
                }

                // 缓存未命中或已过期，查询数据库
                Users user = userMapper.findByUserName(username).stream().findFirst().orElse(null);
                if (user != null && user.getId() != null) {
                    // 缓存结果并记录时间戳
                    usernameToUserIdCache.put(username, user.getId());
                    cacheTimestamps.put(username, System.currentTimeMillis());
                    log.debug("从数据库查询并缓存用户ID: {} for username: {}", user.getId(), username);
                    return user.getId();
                }
                log.warn("未找到用户名为 {} 的用户", username);
            }

        } catch (Exception e) {
            log.error("获取当前用户ID时发生错误", e);
        }
        // 返回null，让调用处决定是否兜底
        return null;
    }
    
    /**
     * 获取当前登录用户的用户名
     *
     * @return 当前用户名，如果未登录则返回null
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
     * 获取当前登录用户的完整信息
     * 使用缓存机制避免重复查询数据库
     *
     * @return 当前用户信息，如果未登录或用户不存在则返回null
     */
    public Users getCurrentUser() {
        try {
            String username = getCurrentUsername();
            if (!StringUtils.hasText(username)) {
                return null;
            }

            // 先从缓存查找（检查 TTL）
            if (!isCacheExpired(username)) {
                Users cachedUser = usernameToUserCache.get(username);
                if (cachedUser != null) {
                    log.debug("从缓存获取到用户信息 for username: {}", username);
                    return cachedUser;
                }
            } else {
                evictCache(username);
            }

            // 缓存未命中或已过期，查询数据库
            Users user = userMapper.findByUserName(username).stream().findFirst().orElse(null);
            if (user != null) {
                // 缓存结果并记录时间戳
                usernameToUserCache.put(username, user);
                usernameToUserIdCache.put(username, user.getId());
                cacheTimestamps.put(username, System.currentTimeMillis());
                log.debug("从数据库查询并缓存用户信息 for username: {}", username);
                return user;
            }
            log.warn("未找到用户名为 {} 的用户信息", username);
        } catch (Exception e) {
            log.error("获取当前用户信息时发生错误", e);
        }
        return null;
    }
    
    /**
     * 检查当前用户是否已登录
     * 
     * @return true表示已登录，false表示未登录
     */
    public boolean isCurrentUserLoggedIn() {
        return getCurrentUserId() != null;
    }
    
    /**
     * 检查当前用户是否为指定用户
     *
     * @param userId 要检查的用户ID
     * @return true表示是当前用户，false表示不是
     */
    public boolean isCurrentUser(Long userId) {
        if (userId == null) {
            return false;
        }
        Long currentUserId = getCurrentUserId();
        return userId.equals(currentUserId);
    }

    /**
     * 清除指定用户的缓存
     * 用于用户信息更新后清除缓存，确保数据一致性
     *
     * @param username 要清除缓存的用户名
     */
    public void clearUserCache(String username) {
        if (StringUtils.hasText(username)) {
            usernameToUserIdCache.remove(username);
            usernameToUserCache.remove(username);
            cacheTimestamps.remove(username);
            log.debug("已清除用户 {} 的缓存", username);
        }
    }

    /**
     * 清除所有用户缓存
     * 用于系统维护或批量数据更新时清除所有缓存
     */
    public void clearAllUserCache() {
        int userIdCacheSize = usernameToUserIdCache.size();
        int userCacheSize = usernameToUserCache.size();

        usernameToUserIdCache.clear();
        usernameToUserCache.clear();
        cacheTimestamps.clear();

        log.info("已清除所有用户缓存，用户ID缓存: {} 项，用户信息缓存: {} 项", userIdCacheSize, userCacheSize);
    }
}