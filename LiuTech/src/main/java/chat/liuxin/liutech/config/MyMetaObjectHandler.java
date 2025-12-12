package chat.liuxin.liutech.config;

import java.util.Date;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

import chat.liuxin.liutech.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis-Plus 字段自动填充处理器
 * 自动填充BaseEntity中的通用字段
 * 使用 UserUtils 统一从Spring Security上下文获取当前用户信息
 *
 * 作者：刘鑫，时间：2025-08-26（Asia/Shanghai）
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Autowired
    private UserUtils userUtils; // 使用统一的用户工具类，避免重复代码

    /**
     * 插入时自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictInsertFill(metaObject, "createdAt", Date.class, now);
        this.strictInsertFill(metaObject, "updatedAt", Date.class, now);

        Long currentUserId = userUtils.getCurrentUserId();
        this.strictInsertFill(metaObject, "createdBy", Long.class, currentUserId);
        this.strictInsertFill(metaObject, "updatedBy", Long.class, currentUserId);
    }
    
    
    /**
     * 更新时自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictUpdateFill(metaObject, "updatedAt", Date.class, now);

        Long currentUserId = userUtils.getCurrentUserId();
        if (currentUserId != null) {
            this.strictUpdateFill(metaObject, "updatedBy", Long.class, currentUserId);
        }
    }
}