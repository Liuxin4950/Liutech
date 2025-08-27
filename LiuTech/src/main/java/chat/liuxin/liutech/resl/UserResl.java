package chat.liuxin.liutech.resl;


import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class UserResl {
    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 用户积分
     */
    private BigDecimal points;

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

    /**
     * 密码哈希（管理端使用，前端不返回）
     */
    private String passwordHash;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}