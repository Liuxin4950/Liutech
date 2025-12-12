package chat.liuxin.liutech.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 安全的用户响应基类
 * 继承 Users 实体类，但排除敏感字段（如密码哈希）
 * 用于响应类继承，确保敏感信息不会泄露到API响应中
 *
 * @author 刘鑫
 * @date 2025-12-12
 */
public class UsersSafe extends Users {

    /**
     * 重写 getPasswordHash 方法，在序列化为JSON时忽略密码字段
     * 确保密码哈希不会出现在API响应中
     *
     * @return 始终返回 null
     */
    @Override
    @JsonIgnore
    public String getPasswordHash() {
        return null;
    }
}