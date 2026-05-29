package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.application.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.AiPromptSecurityPolicy;
import chat.liuxin.ai.infra.security.AiSystemPromptProvider;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 聊天 Handler。
 *
 * <p>处理普通闲聊、问答等非工具型请求。
 * 使用 chatWithoutTools() 纯文本生成，不注册任何 MCP 工具。
 * 这样避免了双重搜索问题：Handler 本身不执行搜索，模型也不会自主搜索。
 *
 * @author liuxin
 */
@Component
@RequiredArgsConstructor
public class AgentChatHandler implements AgentIntentHandler {

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final AiSystemPromptProvider systemPromptProvider;
    private final AiPromptSecurityPolicy promptSecurityPolicy;
    private final AiModelPolicy aiModelPolicy;

    @Override
    public AgentChatResponse handle(AgentChatRequest request, AgentHandlerContext ctx) {
        String prompt = promptSecurityPolicy.wrapUntrustedContent(
                "USER_MESSAGE",
                "请以 LiuTech 博客站内看板娘的口吻自然回复。\n\n用户消息：\n" + request.getMessage());

        String answer = generateText(prompt, 384);

        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .conversationId(ctx.getConversationId())
                .handlerName("chat")
                .message(answer)
                .build();
    }

    /**
     * 生成文本（无工具）。
     * 使用 chatWithoutTools 避免双重搜索。
     */
    private String generateText(String prompt, int maxTokens) {
        try {
            List<Message> messages = List.of(
                    new SystemMessage(systemPromptProvider.buildSystemPrompt()),
                    new UserMessage(prompt));
            return siliconFlowChatClient.chatWithoutTools(messages, aiModelPolicy.resolveModelName((String) null), 0.6, maxTokens);
        } catch (Exception e) {
            return "抱歉，AI 服务暂时不可用，请稍后再试。";
        }
    }
}



