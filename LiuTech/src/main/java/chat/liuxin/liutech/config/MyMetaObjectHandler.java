package chat.liuxin.liutech.config;

import java.util.Date;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis-Plus 字段自动填充处理器
 * 自动填充BaseEntity中的通用字段
 * 支持从Spring Security上下文获取当前用户信息
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        
        // 自动填充创建时间
        this.strictInsertFill(metaObject, "createdAt", Date.class, now);
        
        // 自动填充更新时间
        this.strictInsertFill(metaObject, "updatedAt", Date.class, now);
        
        // 从Spring Security上下文获取当前用户ID
        Long currentUserId = getCurrentUserId();
        
        // 自动填充创建人ID
        this.strictInsertFill(metaObject, "createdBy", Long.class, currentUserId);
        
        // 自动填充更新人ID
        this.strictInsertFill(metaObject, "updatedBy", Long.class, currentUserId);
    }
    
    /**
     * 从Spring Security上下文获取当前用户ID
     * 
     * @return 当前用户ID，如果未认证则返回默认值1L
     */
    private Long getCurrentUserId() {
        try {
            // 获取当前认证信息
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated() 
                && !"anonymousUser".equals(authentication.getPrincipal())) {
                
                // 获取用户名（在JWT过滤器中设置的principal）
                String username = (String) authentication.getPrincipal();
                
                if (StringUtils.hasText(username)) {
                    // TODO: 这里可以根据用户名查询数据库获取用户ID
                    // 暂时使用简单的转换逻辑，实际项目中应该查询用户表
                    log.debug("当前认证用户: {}", username);
                    
                    // 示例：如果用户名是数字，直接转换；否则返回默认值
                    try {
                        return Long.parseLong(username);
                    } catch (NumberFormatException e) {
                        // 如果用户名不是数字，可以通过用户服务查询用户ID
                        // 这里暂时返回默认值，实际应该注入UserService进行查询
                        log.warn("无法从用户名 {} 解析用户ID，使用默认值", username);
                        return 1L;
                    }
                }
            }
            
            log.debug("未找到认证信息，使用默认用户ID");
            return 1L;
            
        } catch (Exception e) {
            log.error("获取当前用户ID时发生错误: {}", e.getMessage());
            return 1L;
        }
    }

    /**
     * 更新时自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        Date now = new Date();
        
        // 自动填充更新时间
        this.strictUpdateFill(metaObject, "updatedAt", Date.class, now);
        
        // 从Spring Security上下文获取当前用户ID
        Long currentUserId = getCurrentUserId();
        
        // 自动填充更新人ID
        this.strictUpdateFill(metaObject, "updatedBy", Long.class, currentUserId);
    }
}