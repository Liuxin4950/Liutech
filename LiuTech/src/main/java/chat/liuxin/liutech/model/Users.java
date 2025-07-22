package chat.liuxin.liutech.model;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 用户表
 * @TableName users
 */
@Data
@TableName("users")
public class Users {
    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 密码哈希
     */
    private String passwordHash;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 用户积分
     */
    private BigDecimal points;

    /**
     * 用户状态(0禁用,1正常)
     */
    private Integer status;

    /**
     * 最近登录时间
     */
    private Date lastLoginAt;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 删除时间（软删除）
     */
    private Date deletedAt;

}