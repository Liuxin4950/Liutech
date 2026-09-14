package chat.liuxin.liutech.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 提供给受信任内部服务的最小身份信息。 */
@Data
@AllArgsConstructor
public class AuthIntrospectionResp {
    private Long userId;
    private String username;
    private String role;
}
