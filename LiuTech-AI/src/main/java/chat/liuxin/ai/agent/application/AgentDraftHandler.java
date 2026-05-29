package chat.liuxin.ai.agent.application;
import chat.liuxin.ai.dto.AgentUserContext;

import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.agent.response.ConfirmationRequiredPayload;
import chat.liuxin.ai.infra.security.AiCapabilityResolver;
import chat.liuxin.ai.infra.security.AiToolAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 创建草稿 Handler。
 *
 * <p>管理员创建文章草稿。流程：
 * <ol>
 *   <li>权限校验（必须是管理员）</li>
 *   <li>校验草稿内容（必须有分类）</li>
 *   <li>创建确认卡片（等待管理员确认）</li>
 * </ol>
 *
 * @author liuxin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDraftHandler implements AgentIntentHandler {

    private final AiToolAccessPolicy toolAccessPolicy;
    private final AiCapabilityResolver capabilityResolver;

    

    @Override
    public AgentChatResponse handle(AgentChatRequest request, AgentHandlerContext ctx) {
        AgentUserContext user = ctx.getUser();

        // 权限校验
        if (!ctx.isAdmin()) {
            return buildNoPermissionResponse(ctx);
        }
        toolAccessPolicy.assertWriteAllowed(user);

        // 校验草稿内容
        AdminArticleDraftRequest draft = request.getDraft();
        if (draft == null || draft.getCategoryId() == null) {
            return AgentChatResponse.builder()
                    .success(true)
                    .taskId(ctx.getTaskId())
                    .conversationId(ctx.getConversationId())
                    .handlerName("draft")
                    
                    .message("创建文章需要先选择分类。请在编辑器里选好分类后再让我保存草稿。")
                    .build();
        }

        // 创建确认卡片（不直接保存，等待管理员确认）
        ConfirmationRequiredPayload confirmation = ConfirmationRequiredPayload.builder()
                .actionType("CREATE_DRAFT")
                .title("确认创建文章草稿")
                .description("确认后会调用主后端创建草稿，不会直接发布。")
                .preview(draft)
                .riskLevel("medium")
                .build();

        log.info("创建草稿确认卡片: userId={}, categoryId={}", user.getUserId(), draft.getCategoryId());

        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .conversationId(ctx.getConversationId())
                .handlerName("draft")
                
                .message("我已经生成草稿预览。确认后只会保存为草稿，不会直接发布。")
                .confirmation(confirmation)
                .build();
    }

    private AgentChatResponse buildNoPermissionResponse(AgentHandlerContext ctx) {
        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .handlerName("draft")
                
                .message("这类写入能力只有管理员可以使用。我可以继续帮你做只读总结、搜索和推荐。")
                .role("user")
                .authenticated(false)
                .admin(false)
                .capabilities(capabilityResolver.resolve(ctx.getUser()).getCapabilities())
                .build();
    }
}








