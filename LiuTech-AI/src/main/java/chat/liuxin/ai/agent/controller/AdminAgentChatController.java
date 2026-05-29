package chat.liuxin.ai.agent.controller;

import chat.liuxin.ai.agent.application.AgentService;
import chat.liuxin.ai.agent.application.AgentUserContextResolver;
import chat.liuxin.ai.agent.application.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.agent.response.AgentErrorPayload;
import chat.liuxin.ai.agent.response.AgentSseEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

/**
 * 管理员 Agent 写作控制器。
 *
 * <p>管理员写作助手专用入口，通过 /ai/admin/** 安全规则保护。
 */
@Slf4j
@RestController
@RequestMapping("/ai/admin/agent")
@RequiredArgsConstructor
public class AdminAgentChatController {

    private final AgentService agentService;
    private final AgentUserContextResolver userContextResolver;

    @Value("${spring.ai.sse.timeout:300000}")
    private long sseTimeout;

    /**
     * 管理员非流式聊天。
     */
    @PostMapping("/chat")
    public AgentChatResponse chat(
            @Valid @RequestBody AgentChatRequest request,
            HttpServletRequest servletRequest) {
        return agentService.chatAdmin(request, userContextResolver.resolve(servletRequest));
    }

    /**
     * 管理员流式聊天。
     */
    @PostMapping("/stream")
    public SseEmitter stream(
            @Valid @RequestBody AgentChatRequest request,
            HttpServletRequest servletRequest) {
        SseEmitter emitter = new SseEmitter(sseTimeout);

        // 注册回调
        emitter.onTimeout(() -> log.info("Admin SSE 超时: conversationId={}", request.getConversationId()));
        emitter.onCompletion(() -> log.debug("Admin SSE 完成: conversationId={}", request.getConversationId()));
        emitter.onError(e -> log.warn("Admin SSE 错误: conversationId={}, error={}", request.getConversationId(), e.getMessage()));

        // 异步执行
        var user = userContextResolver.resolve(servletRequest);
        CompletableFuture.runAsync(() -> {
            try {
                agentService.chatStreamAdmin(request, user, emitter);
            } catch (Exception e) {
                log.error("Admin Agent 流式执行异常: conversationId={}", request.getConversationId(), e);
                sendErrorEvent(emitter, request.getConversationId(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sendErrorEvent(SseEmitter emitter, Long conversationId, Exception e) {
        try {
            var errorPayload = AgentErrorPayload.of("ADMIN_AGENT_ERROR",
                    "管理员写作助手执行失败: " + (e.getMessage() == null ? "未知错误" : e.getMessage()), "execute");
            var envelope = AgentSseEnvelope.of("error", null, conversationId, errorPayload);
            emitter.send(SseEmitter.event().name("error").data(envelope));
        } catch (Exception sendError) {
            log.warn("发送 Admin SSE 错误事件失败: {}", sendError.getMessage());
        }
    }
}


