package chat.liuxin.liutech.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import lombok.extern.slf4j.Slf4j;

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
    private UserMapper userMapper;
    
    /**
     * 从Spring Security上下文获取当前用户ID
     * 优先策略：
     * 1) 若 Authentication 是 UsernamePasswordAuthenticationToken，且 details 中放了 userId（由Jwt过滤器设置），直接取用；
     * 2) 若 principal 是用户名，则调用 UserMapper.findByUserName 查询数据库获取用户ID；
     * 3) 否则返回 null（调用处可决定是否给默认值）。
     * 
     * @return 当前用户ID，如果未登录或获取失败则返回null
     */
    public Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }
            if ("anonymousUser".equals(authentication.getPrincipal())) {
                return null;
            }

            // 1) 尝试从details中读取（JwtAuthenticationFilter已写入userId）
            if (authentication instanceof UsernamePasswordAuthenticationToken token) {
                Object details = token.getDetails();
                if (details instanceof Long userId) {
                    return userId;
                }
            }

            // 2) 回退：从principal的用户名查询数据库
            Object principal = authentication.getPrincipal();
            if (principal instanceof String username && StringUtils.hasText(username)) {
                // findByUserName 可能返回多条，取第一条
                Users user = userMapper.findByUserName(username).stream().findFirst().orElse(null);
                if (user != null && user.getId() != null) {
                    return user.getId();
                }
            }
        
        } catch (Exception e) {
            log.error("获取当前用户ID时发生错误: {}", e.getMessage());
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
                return null;
            }
            if ("anonymousUser".equals(authentication.getPrincipal())) {
                return null;
            }
            
            Object principal = authentication.getPrincipal();
            if (principal instanceof String username && StringUtils.hasText(username)) {
                return username;
            }
        } catch (Exception e) {
            log.error("获取当前用户名时发生错误: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 获取当前登录用户的完整信息
     * 
     * @return 当前用户信息，如果未登录或用户不存在则返回null
     */
    public Users getCurrentUser() {
        try {
            String username = getCurrentUsername();
            if (!StringUtils.hasText(username)) {
                return null;
            }
            
            return userMapper.findByUserName(username).stream().findFirst().orElse(null);
        } catch (Exception e) {
            log.error("获取当前用户信息时发生错误: {}", e.getMessage());
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
}