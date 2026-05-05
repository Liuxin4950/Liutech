package chat.liuxin.ai.agent.controller;

import chat.liuxin.ai.agent.application.AgentActionService;
import chat.liuxin.ai.agent.application.AgentUserContextResolver;
import chat.liuxin.ai.agent.request.AgentActionConfirmRequest;
import chat.liuxin.ai.agent.response.AgentActionResultResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 Agent 动作确认入口。
 */
@RestController
@RequestMapping("/ai/admin/agent/actions")
@RequiredArgsConstructor
public class AdminAgentActionController {

    private final AgentActionService agentActionService;
    private final AgentUserContextResolver userContextResolver;

    @PostMapping("/{actionId}/confirm")
    public AgentActionResultResponse confirm(
            @PathVariable Long actionId,
            @RequestBody(required = false) AgentActionConfirmRequest request,
            HttpServletRequest servletRequest) {
        if (request != null && Boolean.FALSE.equals(request.getConfirmed())) {
            return AgentActionResultResponse.builder()
                    .success(false)
                    .message("管理员已取消该操作")
                    .actionId(actionId)
                    .status("CANCELLED")
                    .build();
        }
        return agentActionService.confirm(actionId, userContextResolver.resolve(servletRequest));
    }
}
