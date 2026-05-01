package chat.liuxin.ai.agent.controller;

import chat.liuxin.ai.agent.application.AgentOrchestrator;
import chat.liuxin.ai.agent.application.AgentUserContextResolver;
import chat.liuxin.ai.agent.request.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentOrchestrator agentOrchestrator;
    private final AgentUserContextResolver userContextResolver;

    @Value("${spring.ai.sse.timeout:180000}")
    private long sseTimeout;

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request, HttpServletRequest servletRequest) {
        return agentOrchestrator.execute(request, userContextResolver.resolve(servletRequest));
    }

    @PostMapping("/stream")
    public SseEmitter stream(@Valid @RequestBody AgentChatRequest request, HttpServletRequest servletRequest) {
        SseEmitter emitter = new SseEmitter(sseTimeout);
        var user = userContextResolver.resolve(servletRequest);
        CompletableFuture.runAsync(() -> {
            try {
                agentOrchestrator.executeStream(request, user, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(java.util.Map.of(
                            "code", "AGENT_ERROR",
                            "message", e.getMessage() == null ? "Agent 执行失败" : e.getMessage(),
                            "stage", "execute")));
                } catch (Exception ignore) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
