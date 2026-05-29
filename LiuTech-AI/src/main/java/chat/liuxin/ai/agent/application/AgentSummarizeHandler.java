package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.application.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.dto.PostDetailDTO;
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
 * SUMMARIZE 意图处理器。
 *
 * 总结当前正在阅读的文章，提取关键点并给出建议。
 * 如果没有当前文章上下文，降级为普通文本生成。
 */
@Component
@RequiredArgsConstructor
public class AgentSummarizeHandler implements AgentIntentHandler {

    private final chat.liuxin.ai.agent.application.PublicArticleTool publicArticleTool;
    private final SiliconFlowChatClient siliconFlowChatClient;
    private final AiSystemPromptProvider systemPromptProvider;
    private final AiPromptSecurityPolicy promptSecurityPolicy;
    private final AiModelPolicy aiModelPolicy;

    

    @Override
    public AgentChatResponse handle(AgentChatRequest request, AgentHandlerContext ctx) {
        PostDetailDTO currentArticle = resolveCurrentArticle(request, ctx);
        if (currentArticle == null) {
            return handleTextGeneration(request, ctx, "请结合用户提供的上下文做总结；如果没有文章内容，说明需要先打开具体文章。");
        }

        String prompt = promptSecurityPolicy.wrapUntrustedContent("CURRENT_ARTICLE_SUMMARY_INPUT", """
                请总结当前文章，要求：
                - 先用 2-3 句话说明文章讲了什么。
                - 再列出 3-5 个关键点。
                - 最后给一个适合继续阅读或实践的建议。
                - 只基于下面的文章内容，不要编造未出现的信息。

                用户要求：
                %s

                文章内容：
                %s
                """.formatted(request.getMessage(), limitText(currentArticle.toAiReadableFormat(), 6000)));

        var card = publicArticleTool.currentArticleCard(currentArticle, "当前文章");
        var payload = card == null ? null : chat.liuxin.ai.agent.response.ArticleResultsPayload.builder()
                .source("current")
                .query(String.valueOf(currentArticle.getId()))
                .reason("当前正在阅读的文章")
                .items(List.of(card))
                .build();

        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .conversationId(ctx.getConversationId())
                .handlerName("summarize")
                
                .message(generateText(prompt, 700))
                .articleResults(payload)
                .build();
    }

    private AgentChatResponse handleTextGeneration(AgentChatRequest request, AgentHandlerContext ctx, String instruction) {
        String answer = generateText(promptSecurityPolicy.wrapUntrustedContent(
                "USER_MESSAGE", instruction + "\n\n用户消息：\n" + request.getMessage()), 384);
        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .conversationId(ctx.getConversationId())
                .handlerName("summarize")
                
                .message(answer)
                .build();
    }

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

    private PostDetailDTO resolveCurrentArticle(AgentChatRequest request, AgentHandlerContext ctx) {
        Long postId = resolvePostId(request);
        if (postId == null) return null;
        return publicArticleTool.getArticleDetail(postId);
    }

    private Long resolvePostId(AgentChatRequest request) {
        if (request.getDraft() != null && request.getDraft().getPostId() != null) return request.getDraft().getPostId();
        if (request.getContext() != null) {
            Object postId = request.getContext().get("postId");
            if (postId instanceof Number n) return n.longValue();
            if (postId instanceof String s) { try { return Long.parseLong(s); } catch (NumberFormatException ignore) {} }
        }
        return null;
    }

    private String limitText(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "\n\n（内容已截断）";
    }
}






