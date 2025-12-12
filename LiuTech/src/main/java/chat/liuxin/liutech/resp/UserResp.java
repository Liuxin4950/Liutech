package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.UsersSafe;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户响应
 * 继承 UsersSafe 安全基类，排除敏感字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserResp extends UsersSafe {

    /**
     * 文章数量
     */
    private Integer postCount;
}
