package chat.liuxin.liutech.config;

import java.util.Date;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired; // 新增
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // 新增
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

import chat.liuxin.liutech.mapper.UserMapper; // 新增：查询用户ID
import chat.liuxin.liutech.model.Users;       // 新增：用户实体
import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis-Plus 字段自动填充处理器
 * 自动填充BaseEntity中的通用字段
 * 支持从Spring Security上下文获取当前用户信息
 *
 * 作者：刘鑫，时间：2025-08-26（Asia/Shanghai）
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Autowired
    private UserMapper userMapper; // 说明：用于通过用户名查询用户ID，避免默认值1L的问题

    /**
     * 插入时自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictInsertFill(metaObject, "createdAt", Date.class, now);
        this.strictInsertFill(metaObject, "updatedAt", Date.class, now);

        Long currentUserId = getCurrentUserId();
        this.strictInsertFill(metaObject, "createdBy", Long.class, currentUserId);
        this.strictInsertFill(metaObject, "updatedBy", Long.class, currentUserId);
    }
    
    /**
     * 从Spring Security上下文获取当前用户ID
     * 优先策略：
     * 1) 若 Authentication 是 UsernamePasswordAuthenticationToken，且 details 中放了 userId（由Jwt过滤器设置），直接取用；
     * 2) 若 principal 是用户名，则调用 UserMapper.findByUserName 查询数据库获取用户ID；
     * 3) 否则返回 null（调用处可决定是否给默认值）。
     */
    private Long getCurrentUserId() {
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
     * 更新时自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictUpdateFill(metaObject, "updatedAt", Date.class, now);

        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            this.strictUpdateFill(metaObject, "updatedBy", Long.class, currentUserId);
        }
    }
}