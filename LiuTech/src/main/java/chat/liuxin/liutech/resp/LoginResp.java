package chat.liuxin.liutech.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应结果类
 * 只返回JWT token字符串，客户端使用时需要在Authorization头中添加"Bearer "前缀
 *
 * @author liuxin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResp {

    /**
     * JWT访问令牌
     * 客户端需要在后续请求的Authorization头中携带此token
     * 格式：Bearer {token}
     */
    private String token;
}
