package chat.liuxin.ai.agent.controller;

import chat.liuxin.ai.agent.application.AgentOrchestrator;
import chat.liuxin.ai.agent.application.AgentUserContextResolver;
import chat.liuxin.ai.agent.request.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
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
 * 后端通过 context.page 自动判断写作意图，无需前端额外标记。
 */
@Slf4j
@RestController
@RequestMapping("/ai/admin/agent")
@RequiredArgsConstructor
public class AdminAgentChatController {

    private final AgentOrchestrator agentOrchestrator;
    private final AgentUserContextResolver userContextResolver;

    @Value("${spring.ai.sse.timeout:300000}")
    private long sseTimeout;

    @PostMapping("/chat")
    public AgentChatResponse chat(
            @Valid @RequestBody AgentChatRequest request,
            HttpServletRequest servletRequest) {
        return agentOrchestrator.executeAdmin(request, userContextResolver.resolve(servletRequest));
    }

    @PostMapping("/stream")
    public SseEmitter stream(
            @Valid @RequestBody AgentChatRequest request,
            HttpServletRequest servletRequest) {
        SseEmitter emitter = new SseEmitter(sseTimeout);
        emitter.onTimeout(() -> log.info("Admin Agent SSE 连接超时: conversationId={}", request.getConversationId()));
        emitter.onCompletion(() -> log.debug("Admin Agent SSE 连接完成: conversationId={}", request.getConversationId()));
        emitter.onError(e -> log.warn("Admin Agent SSE 连接错误: conversationId={}, error={}", request.getConversationId(), e.getMessage()));

        var user = userContextResolver.resolve(servletRequest);
        CompletableFuture.runAsync(() -> {
            try {
                agentOrchestrator.executeAdminStream(request, user, emitter);
            } catch (Exception e) {
                log.error("Admin Agent 流式执行异常: conversationId={}", request.getConversationId(), e);
                try {
                    var errorPayload = chat.liuxin.ai.agent.response.AgentErrorPayload.of(
                            "ADMIN_AGENT_ERROR",
                            "管理员写作助手执行失败: " + (e.getMessage() == null ? "未知错误" : e.getMessage()),
                            "execute");
                    var envelope = chat.liuxin.ai.agent.response.AgentSseEnvelope.of(
                            "error", null, request.getConversationId(), errorPayload);
                    emitter.send(SseEmitter.event().name("error").data(envelope));
                } catch (Exception sendError) {
                    log.warn("发送 Admin Agent SSE 错误事件失败: {}", sendError.getMessage());
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
