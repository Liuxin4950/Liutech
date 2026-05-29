package chat.liuxin.ai.agent.application;
import chat.liuxin.ai.dto.AgentUserContext;

import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.agent.response.AgentErrorCode;
import chat.liuxin.ai.agent.response.AgentErrorStage;
import chat.liuxin.ai.agent.response.AgentPlanStep;
import chat.liuxin.ai.infra.security.AiCapabilityResolver;
import chat.liuxin.ai.infra.security.AiCapabilityContext;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.AiPromptSecurityPolicy;
import chat.liuxin.ai.infra.security.AiSystemPromptProvider;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 核心服务。
 *
 * <p>职责：接收请求，路由到对应 Handler 执行。
 *
 * <p>路由规则（简单优先）：
 * <ul>
 *   <li>管理员在编辑页 → WritingHandler</li>
 *   <li>管理员发布/下架操作 → PublishHandler/OfflineHandler</li>
 *   <li>包含搜索关键词 → SearchHandler</li>
 *   <li>其他 → ChatHandler</li>
 * </ul>
 *
 * @author liuxin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentStreamPublisher streamPublisher;
    private final AgentChatHandler chatHandler;
    private final AgentSearchHandler searchHandler;
    private final AgentWritingHandler writingHandler;
    private final AgentPublishHandler publishHandler;
    private final AgentOfflineHandler offlineHandler;
    private final AgentDraftHandler draftHandler;
    private final AgentRecommendHandler recommendHandler;
    private final AgentIdentityHandler identityHandler;
    private final AgentSummarizeHandler summarizeHandler;
    private final AiCapabilityResolver capabilityResolver;
    private final SpeechOrchestrationService speechOrchestrationService;

    /**
     * 非流式聊天。
     *
     * @param request 聊天请求
     * @param user    用户上下文（可为 null，表示访客）
     * @return 聊天响应
     */
    public AgentChatResponse chat(AgentChatRequest request, AgentUserContext user) {
        Long taskId = generateTaskId();
        String handlerName = resolveHandlerName(request, user);
        log.info("Agent 聊天: handler={}, taskId={}, userId={}", handlerName, taskId, getUserId(user));

        AgentChatResponse response = executeHandler(handlerName, request, user, taskId, null);
        enrichSecurityContext(response, user);
        return response;
    }

    /**
     * 管理员非流式聊天。
     */
    public AgentChatResponse chatAdmin(AgentChatRequest request, AgentUserContext user) {
        requireAdmin(user);
        return chat(request, user);
    }

    /**
     * 流式聊天（SSE）。
     *
     * @param request 聊天请求
     * @param user    用户上下文
     * @param emitter SSE 发射器
     */
    public void chatStream(AgentChatRequest request, AgentUserContext user, SseEmitter emitter) {
        Long taskId = generateTaskId();
        String handlerName = resolveHandlerName(request, user);
        log.info("Agent 流式聊天: handler={}, taskId={}, userId={}", handlerName, taskId, getUserId(user));

        AgentSseContext context = AgentSseContext.of(emitter, taskId, request.getConversationId());

        // 发送启动事件
        AiCapabilityContext cap = capabilityResolver.resolve(user);
        streamPublisher.sendAgentStart(emitter, taskId, request.getConversationId(),
                handlerName, cap.getRole(), cap.getCapabilities());

        // 执行 Handler
        AgentChatResponse response = executeHandler(handlerName, request, user, taskId, context);
        enrichSecurityContext(response, user);

        // 发布响应事件
        publishResponseEvents(context, response);

        // 发布语音事件
        speechOrchestrationService.publishSpeechAndCues(context, request, response);

        // 发送完成事件
        streamPublisher.sendComplete(emitter, taskId, request.getConversationId());
        emitter.complete();
    }

    /**
     * 管理员流式聊天。
     */
    public void chatStreamAdmin(AgentChatRequest request, AgentUserContext user, SseEmitter emitter) {
        requireAdmin(user);
        chatStream(request, user, emitter);
    }

    // ===== Handler 路由 =====

    /**
     * 根据请求内容和用户角色判断应该使用哪个 Handler。
     */
    private String resolveHandlerName(AgentChatRequest request, AgentUserContext user) {
        String message = request.getMessage();
        if (message == null) message = "";
        String lower = message.trim().toLowerCase();

        // 管理员在编辑页 → 写作
        if (isAdmin(user) && isEditingPage(request)) {
            return "writing";
        }

        // 管理员发布/下架操作
        if (isAdmin(user)) {
            if (containsAny(lower, "发布", "上线")) {
                return "publish";
            }
            if (containsAny(lower, "下架", "下线", "撤下", "取消发布")) {
                return "offline";
            }
            if (containsAny(lower, "保存草稿", "创建草稿", "生成草稿")) {
                return "draft";
            }
        }

        // 身份查询
        if (containsAny(lower, "我是谁", "我是啥身份", "我是什么身份", "我的身份", "我是什么角色", "我的角色",
                "是否登录", "有没有登录", "登录了吗", "我是管理员", "我是不是管理员", "权限")) {
            return "identity";
        }

        // 搜索文章
        if (containsAny(lower, "搜索", "查找", "找一下", "找找")) {
            return "search";
        }

        // 推荐文章
        if (containsAny(lower, "推荐", "类似文章", "相关文章", "有什么文章", "想看", "想学", "学习", "了解")) {
            return "recommend";
        }

        // 总结文章
        if (containsAny(lower, "总结", "概括", "讲了什么", "讲了啥", "讲什么", "说了什么", "说了啥")) {
            return "summarize";
        }

        // 写作（非管理员也可以获取写作建议）
        if (containsAny(lower, "写一篇", "帮我写", "写博客", "写文章", "润色", "扩写", "摘要", "标题",
                "整理", "富文本", "丰富", "优化内容", "改进文章", "续写", "改写", "排版", "扩充")) {
            return "writing";
        }

        // 默认聊天
        return "chat";
    }

    private boolean isEditingPage(AgentChatRequest request) {
        Map<String, Object> ctx = request.getContext();
        Object page = ctx == null ? null : ctx.get("page");
        return "admin-post-editor".equals(page) || "web-create-post".equals(page);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private AgentChatResponse executeHandler(
            String handlerName,
            AgentChatRequest request,
            AgentUserContext user,
            Long taskId,
            AgentSseContext context) {
        try {
            AgentHandlerContext handlerCtx = AgentHandlerContext.of(taskId, user, null,
                    context != null ? context.getEmitter() : null, request.getConversationId());

            return switch (handlerName) {
                case "writing" -> writingHandler.handle(request, handlerCtx);
                case "search" -> searchHandler.handle(request, handlerCtx);
                case "recommend" -> recommendHandler.handle(request, handlerCtx);
                case "draft" -> draftHandler.handle(request, handlerCtx);
                case "publish" -> publishHandler.handle(request, handlerCtx);
                case "offline" -> offlineHandler.handle(request, handlerCtx);
                case "identity" -> identityHandler.handle(request, handlerCtx);
                case "summarize" -> summarizeHandler.handle(request, handlerCtx);
                default -> chatHandler.handle(request, handlerCtx);
            };
        } catch (Exception e) {
            log.error("Agent Handler 执行失败: handler={}, taskId={}", handlerName, taskId, e);
            return AgentChatResponse.builder()
                    .success(false)
                    .taskId(taskId)
                    .conversationId(request.getConversationId())
                    .handlerName(handlerName)
                    .message("抱歉，处理请求时出现问题，请稍后再试。")
                    .build();
        }
    }

    // ===== SSE 事件发布 =====

    private void publishResponseEvents(AgentSseContext context, AgentChatResponse response) {
        if (response == null) return;

        // 错误事件
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            streamPublisher.error(context.getEmitter(),
                    AgentErrorCode.AGENT_ERROR.getCode(),
                    response.getMessage(),
                    AgentErrorStage.EXECUTE.getStage(),
                    context.getTaskId(),
                    context.getConversationId());
        }

        // 文本事件
        if (response.getMessage() != null && !response.getMessage().isBlank()) {
            streamPublisher.sendData(context.getEmitter(), context.getTaskId(),
                    context.getConversationId(), response.getMessage());
        }

        // 文章结果事件
        if (response.getArticleResults() != null) {
            streamPublisher.send(context.getEmitter(), "article-results",
                    context.getTaskId(), context.getConversationId(), response.getArticleResults());
        }

        // 写作草稿事件
        if (response.getWritingDraft() != null) {
            streamPublisher.send(context.getEmitter(), "writing-draft",
                    context.getTaskId(), context.getConversationId(), response.getWritingDraft());
        }

        // 字段更新事件
        if (response.getFieldUpdate() != null) {
            streamPublisher.send(context.getEmitter(), "field-update",
                    context.getTaskId(), context.getConversationId(), response.getFieldUpdate());
        }

        // 确认卡片事件
        if (response.getConfirmation() != null) {
            streamPublisher.send(context.getEmitter(), "confirmation-required",
                    context.getTaskId(), context.getConversationId(), response.getConfirmation());
        }
    }

    // ===== 工具方法 =====

    private void enrichSecurityContext(AgentChatResponse response, AgentUserContext user) {
        if (response == null) return;
        AiCapabilityContext cap = capabilityResolver.resolve(user);
        response.setRole(cap.getRole());
        response.setAuthenticated(cap.isAuthenticated());
        response.setAdmin(cap.isAdmin());
        response.setCapabilities(cap.getCapabilities());
    }

    private void requireAdmin(AgentUserContext user) {
        if (!isAdmin(user)) {
            throw new AccessDeniedException("需要管理员权限");
        }
    }

    private boolean isAdmin(AgentUserContext user) {
        return user != null && user.isAdmin();
    }

    private String getUserId(AgentUserContext user) {
        return user == null ? "anonymous" : user.userIdString();
    }

    private Long generateTaskId() {
        return System.currentTimeMillis();
    }
}

