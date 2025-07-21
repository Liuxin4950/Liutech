package chat.liuxin.liutech.resl;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Schema(description = "用户响应信息")
@Data
public class UserResl {
    /**
     * 用户名
     */
    @Schema(description = "用户名", example = "liuxin")
    private String username;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱地址", example = "liuxin@example.com")
    private String email;

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
     * 最近登录时间
     */
    @Schema(description = "最近登录时间", example = "2024-01-01 12:00:00")
    private Date lastLoginAt;
}