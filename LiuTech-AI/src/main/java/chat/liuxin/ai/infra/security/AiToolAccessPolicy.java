package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.dto.AgentUserContext;
import org.springframework.stereotype.Component;

/**
 * 工具访问策略。
 *
 * <p>简单的权限检查：写操作需要管理员权限。
 *
 * @author liuxin
 */
@Component
public class AiToolAccessPolicy {

    /**
     * 检查用户是否有写权限。
     *
     * @param user 用户上下文
     * @return true 如果用户是管理员
     */
    public boolean canWrite(AgentUserContext user) {
        return user != null && user.isAdmin();
    }

    /**
     * 断言用户有写权限，否则抛出异常。
     *
     * @param user 用户上下文
     * @throws AiToolAccessDeniedException 如果用户不是管理员
     */
    public void assertWriteAllowed(AgentUserContext user) {
        if (!canWrite(user)) {
            throw new AiToolAccessDeniedException("需要管理员权限才能执行写操作");
        }
    }
}

