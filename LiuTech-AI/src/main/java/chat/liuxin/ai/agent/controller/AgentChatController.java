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
 * Agent 聊天控制器。
 *
 * <p>提供两种交互方式：
 * <ul>
 *   <li>/chat - 非流式聊天，返回单一 JSON 响应</li>
 *   <li>/stream - SSE 流式聊天，实时推送事件</li>
 * </ul>
 *
 * @author liuxin
 */
@Slf4j
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentService agentService;
    private final AgentUserContextResolver userContextResolver;

    @Value("${spring.ai.sse.timeout:180000}")
    private long sseTimeout;

    /**
     * 非流式聊天。
     */
    @PostMapping("/chat")
    public AgentChatResponse chat(
            @Valid @RequestBody AgentChatRequest request,
            HttpServletRequest servletRequest) {
        return agentService.chat(request, userContextResolver.resolve(servletRequest));
    }

    /**
     * SSE 流式聊天。
     */
    @PostMapping("/stream")
    public SseEmitter stream(
            @Valid @RequestBody AgentChatRequest request,
            HttpServletRequest servletRequest) {
        SseEmitter emitter = new SseEmitter(sseTimeout);

        // 注册回调
        emitter.onTimeout(() -> log.info("SSE 超时: conversationId={}", request.getConversationId()));
        emitter.onCompletion(() -> log.debug("SSE 完成: conversationId={}", request.getConversationId()));
        emitter.onError(e -> log.warn("SSE 错误: conversationId={}, error={}", request.getConversationId(), e.getMessage()));

        // 异步执行
        var user = userContextResolver.resolve(servletRequest);
        CompletableFuture.runAsync(() -> {
            try {
                agentService.chatStream(request, user, emitter);
            } catch (Exception e) {
                log.error("Agent 流式执行异常: conversationId={}", request.getConversationId(), e);
                sendErrorEvent(emitter, request.getConversationId(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sendErrorEvent(SseEmitter emitter, Long conversationId, Exception e) {
        try {
            var errorPayload = AgentErrorPayload.of("AGENT_ERROR",
                    "Agent 执行失败: " + (e.getMessage() == null ? "未知错误" : e.getMessage()), "execute");
            var envelope = AgentSseEnvelope.of("error", null, conversationId, errorPayload);
            emitter.send(SseEmitter.event().name("error").data(envelope));
        } catch (Exception sendError) {
            log.warn("发送 SSE 错误事件失败: {}", sendError.getMessage());
        }
    }
}


