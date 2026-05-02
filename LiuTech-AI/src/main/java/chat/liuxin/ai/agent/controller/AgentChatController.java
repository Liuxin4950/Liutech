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
 * Agent 聊天控制器。
 *
 * <p>提供两种交互方式：
 * <ul>
 *   <li>/chat - 非流式聊天，返回单一 JSON 响应</li>
 *   <li>/stream - SSE 流式聊天，实时推送事件</li>
 * </ul>
 *
 * <p>SSE 生命周期管理：
 * <ul>
 *   <li>超时时间可配置，默认 180 秒</li>
 *   <li>自动注册 onTimeout 和 onCompletion 回调清理资源</li>
 * </ul>
 *
 * @author liuxin
 */
@Slf4j
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentOrchestrator agentOrchestrator;
    private final AgentUserContextResolver userContextResolver;

    @Value("${spring.ai.sse.timeout:180000}")
    private long sseTimeout;

    /**
     * 非流式聊天。
     *
     * @param request         聊天请求
     * @param servletRequest  HTTP 请求
     * @return 聊天响应
     */
    @PostMapping("/chat")
    public AgentChatResponse chat(
            @Valid @RequestBody AgentChatRequest request,
            HttpServletRequest servletRequest) {
        return agentOrchestrator.execute(request, userContextResolver.resolve(servletRequest));
    }

    /**
     * SSE 流式聊天。
     *
     * <p>通过 SSE 向客户端推送实时事件，包括：
     * <ul>
     *   <li>agent-start - 任务启动</li>
     *   <li>agent-plan - 执行计划</li>
     *   <li>tool-start/tool-result - 工具执行</li>
     *   <li>data - 文本内容</li>
     *   <li>article-results - 文章结果</li>
     *   <li>confirmation-required - 确认卡片</li>
     *   <li>complete - 任务完成</li>
     *   <li>error - 错误</li>
     * </ul>
     *
     * <p>所有事件包装为统一 {@link chat.liuxin.ai.agent.response.AgentSseEnvelope} 格式。
     *
     * @param request         聊天请求
     * @param servletRequest  HTTP 请求
     * @return SSE 发射器
     */
    @PostMapping("/stream")
    public SseEmitter stream(
            @Valid @RequestBody AgentChatRequest request,
            HttpServletRequest servletRequest) {
        // 创建 SSE 发射器，设置超时时间
        SseEmitter emitter = new SseEmitter(sseTimeout);

        // 注册超时回调
        emitter.onTimeout(() -> {
            log.info("SSE 连接超时: conversationId={}", request.getConversationId());
        });

        // 注册完成回调
        emitter.onCompletion(() -> {
            log.debug("SSE 连接完成: conversationId={}", request.getConversationId());
        });

        // 注册错误回调
        emitter.onError(e -> {
            log.warn("SSE 连接错误: conversationId={}, error={}", request.getConversationId(), e.getMessage());
        });

        // 获取用户上下文
        var user = userContextResolver.resolve(servletRequest);

        // 异步执行，避免阻塞 HTTP 线程
        CompletableFuture.runAsync(() -> {
            try {
                agentOrchestrator.executeStream(request, user, emitter);
            } catch (Exception e) {
                log.error("Agent 流式执行异常: conversationId={}", request.getConversationId(), e);
                try {
                    // 发送错误事件（使用统一 envelope 格式）
                    var errorPayload = chat.liuxin.ai.agent.response.AgentErrorPayload.of(
                            "AGENT_ERROR",
                            "Agent 执行失败: " + (e.getMessage() == null ? "未知错误" : e.getMessage()),
                            "execute");
                    var envelope = chat.liuxin.ai.agent.response.AgentSseEnvelope.of(
                            "error", null, request.getConversationId(), errorPayload);
                    emitter.send(SseEmitter.event().name("error").data(envelope));
                } catch (Exception sendError) {
                    log.warn("发送 SSE 错误事件失败: {}", sendError.getMessage());
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
