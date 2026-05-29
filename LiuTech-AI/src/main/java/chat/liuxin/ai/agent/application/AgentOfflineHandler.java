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
 * 下架文章 Handler。
 *
 * <p>管理员下架文章。流程：
 * <ol>
 *   <li>权限校验（必须是管理员）</li>
 *   <li>解析文章 ID</li>
 *   <li>创建确认卡片（等待管理员确认）</li>
 * </ol>
 *
 * @author liuxin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOfflineHandler implements AgentIntentHandler {

    private final AiToolAccessPolicy toolAccessPolicy;
    private final AiCapabilityResolver capabilityResolver;

    

    @Override
    public AgentChatResponse handle(AgentChatRequest request, AgentHandlerContext ctx) {
        AgentUserContext user = ctx.getUser();

        // 权限校验
        if (!ctx.isAdmin()) {
            return buildNoPermissionResponse(ctx, "下架文章需要管理员权限。");
        }
        toolAccessPolicy.assertWriteAllowed(user);

        // 解析文章 ID
        Long postId = resolvePostId(request);
        if (postId == null) {
            return AgentChatResponse.builder()
                    .success(true)
                    .taskId(ctx.getTaskId())
                    .conversationId(ctx.getConversationId())
                    .handlerName("offline")
                    
                    .message("请先打开或选择要下架的文章。")
                    .build();
        }

        // 创建确认卡片
        ConfirmationRequiredPayload confirmation = ConfirmationRequiredPayload.builder()
                .actionType("OFFLINE_POST")
                .title("确认下架文章")
                .description("确认后会将该文章状态改为草稿。")
                .preview(java.util.Map.of("postId", postId))
                .riskLevel("high")
                .build();

        log.info("创建下架确认卡片: userId={}, postId={}", user.getUserId(), postId);

        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .conversationId(ctx.getConversationId())
                .handlerName("offline")
                
                .message("我已经定位到目标文章。下架会把文章状态改为草稿，请确认。")
                .confirmation(confirmation)
                .build();
    }

    private Long resolvePostId(AgentChatRequest request) {
        if (request.getDraft() != null && request.getDraft().getPostId() != null) {
            return request.getDraft().getPostId();
        }
        if (request.getContext() != null) {
            Object postId = request.getContext().get("postId");
            if (postId instanceof Number n) return n.longValue();
            if (postId instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignore) {}
            }
        }
        return null;
    }

    private AgentChatResponse buildNoPermissionResponse(AgentHandlerContext ctx, String message) {
        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .handlerName("offline")
                
                .message(message)
                .role("user")
                .authenticated(false)
                .admin(false)
                .capabilities(capabilityResolver.resolve(ctx.getUser()).getCapabilities())
                .build();
    }
}








