package chat.liuxin.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 用户上下文 DTO。
 *
 * <p>封装当前请求的用户信息，用于权限判断和数据访问。
 *
 * @author liuxin
 */
@Data
@Builder
public class AgentUserContext {
    private Long userId;
    private String username;
    private boolean authenticated;
    private boolean admin;
    private String bearerToken;

    public String userIdString() {
        return userId == null ? null : userId.toString();
    }
}
