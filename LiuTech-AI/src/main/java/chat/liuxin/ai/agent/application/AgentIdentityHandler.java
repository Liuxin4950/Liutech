package chat.liuxin.ai.agent.application;
import chat.liuxin.ai.dto.AgentUserContext;

import chat.liuxin.ai.agent.application.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.infra.security.AiCapabilityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * IDENTITY 意图处理器。
 *
 * 响应用户身份查询，返回当前登录状态和可用能力。
 * 纯本地逻辑，不调用 AI 模型。
 */
@Component
@RequiredArgsConstructor
public class AgentIdentityHandler implements AgentIntentHandler {

    private final AiCapabilityResolver capabilityResolver;

    

    @Override
    public AgentChatResponse handle(AgentChatRequest request, AgentHandlerContext ctx) {
        AgentUserContext user = ctx.getUser();
        String message;
        if (user == null || !user.isAuthenticated()) {
            message = "我这边没有识别到你的登录态，所以当前会把你当作访客。访客可以聊天、搜索、推荐和总结公开文章；登录后我就能识别你的账号身份。";
        } else if (ctx.isAdmin()) {
            String name = isBlank(user.getUsername()) ? "你" : user.getUsername();
            message = "我识别到你已登录，身份是管理员（" + name + "）。你可以让我辅助写文章、创建草稿、管理文章发布或下架；涉及发布和下架时，我会先给你确认，不会直接替你执行。";
        } else {
            String name = isBlank(user.getUsername()) ? "你" : user.getUsername();
            message = "我识别到你已登录，身份是普通用户（" + name + "）。你可以聊天、搜索、推荐和总结文章；写博客、发布或下架文章这类管理操作需要管理员权限。";
        }

        var context = capabilityResolver.resolve(user);
        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .conversationId(ctx.getConversationId())
                .handlerName("identity")
                
                .message(message)
                .role(context.getRole())
                .authenticated(context.isAuthenticated())
                .admin(context.isAdmin())
                .capabilities(context.getCapabilities())
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}







