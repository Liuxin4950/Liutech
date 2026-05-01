package chat.liuxin.ai.security;

import chat.liuxin.ai.agent.application.AgentUserContext;
import chat.liuxin.ai.agent.domain.AgentActionType;
import org.springframework.stereotype.Component;

@Component
public class AiToolAccessPolicy {

    public boolean canExecute(AgentUserContext user, AgentActionType actionType) {
        if (actionType == null) {
            return false;
        }
        return switch (actionType) {
            case CREATE_DRAFT, PUBLISH_POST, OFFLINE_POST -> user != null && user.isAdmin();
        };
    }

    public void assertAllowed(AgentUserContext user, AgentActionType actionType) {
        if (!canExecute(user, actionType)) {
            throw new AiToolAccessDeniedException("当前身份不能执行 " + actionType.name() + " 操作");
        }
    }
}
