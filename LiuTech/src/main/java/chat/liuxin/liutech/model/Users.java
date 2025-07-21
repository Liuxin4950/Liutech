package chat.liuxin.liutech.model;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户表
 * @TableName users
 */
@Schema(description = "用户实体")
@Data
@TableName("users")
public class Users {
    /**
     * 用户ID
     */
    @Schema(description = "用户ID", example = "1")
    private Long id;

    /**
     * 用户名
     */
    @Schema(description = "用户名", example = "liuxin", required = true)
    private String username;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱地址", example = "liuxin@example.com", required = true)
    private String email;

    /**
     * 密码哈希
     */
    @Schema(description = "密码哈希值", hidden = true)
    private String passwordHash;

    /**
     * 头像URL
     */
    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    /**
     * 用户积分
     */
    @Schema(description = "用户积分", example = "100.00")
    private BigDecimal points;

    /**
     * 用户状态(0禁用,1正常)
     */
    @Schema(description = "用户状态", example = "1", allowableValues = {"0", "1"})
    private Integer status;

    /**
     * 最近登录时间
     */
    @Schema(description = "最近登录时间", example = "2024-01-01 12:00:00")
    private Date lastLoginAt;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2024-01-01 10:00:00")
    private Date createdAt;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2024-01-01 11:00:00")
    private Date updatedAt;

    /**
     * 创建人ID
     */
    @Schema(description = "创建人ID", example = "1")
    private Long createdBy;

    /**
     * 更新人ID
     */
    @Schema(description = "更新人ID", example = "1")
    private Long updatedBy;

    /**
     * 删除时间
     */
    @Schema(description = "删除时间（软删除）", example = "2024-01-01 13:00:00")
    private Date deletedAt;

}