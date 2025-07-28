package chat.liuxin.liutech.model;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户表
 * @TableName users
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class Users extends BaseEntity {
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
     * 用户昵称
     */
    private String nickname;

    /**
     * 个人简介
     */
    private String bio;

}