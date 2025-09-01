package chat.liuxin.ai.controller;

import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.*;
import chat.liuxin.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;

/**
 * AI聊天控制器
 * 基于微服务架构，Controller层负责接口转发，Service层处理业务逻辑
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@Validated
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * AI聊天接口 - 普通模式
     * Controller层负责接口转发，业务逻辑由Service层处理
     * 
     * @param request 聊天请求参数
     * @return 聊天响应结果
     */
    @PostMapping("/chat")
    public chat.liuxin.ai.resp.ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        log.debug("转发AI聊天请求到Service层 [用户:{}]", request.getUserId());
        return aiChatService.processChat(request);
    }
    
    /**
     * AI聊天接口 - 流式输出模式
     * Controller层负责接口转发，业务逻辑由Service层处理
     * 
     * @param request 聊天请求参数
     * @return SSE流式响应
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        log.debug("转发AI流式聊天请求到Service层 [用户:{}]", request.getUserId());
        return aiChatService.processChatStream(request);
    }
    
    /**
     * 获取用户会话列表
     * Controller层负责接口转发，业务逻辑由Service层处理
     * 
     * @param userId 用户ID
     * @return 会话列表
     */
    @GetMapping("/chat/user/{userId}/sessions")
    public SessionListResponse getUserSessions(@PathVariable String userId) {
        log.debug("转发获取用户会话列表请求到Service层 [用户:{}]", userId);
        return aiChatService.getUserSessions(userId);
    }
    
    /**
     * 获取会话历史记录
     * Controller层负责接口转发，业务逻辑由Service层处理
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 会话历史记录
     */
    @GetMapping("/chat/user/{userId}/sessions/{sessionId}/history")
    public SessionHistoryResponse getSessionHistory(@PathVariable String userId, @PathVariable String sessionId) {
        log.debug("转发获取会话历史记录请求到Service层 [用户:{}, 会话:{}]", userId, sessionId);
        return aiChatService.getSessionHistory(userId, sessionId);
    }
    
    /**
     * 清理用户记忆
     * Controller层负责接口转发，业务逻辑由Service层处理
     * 
     * @param userId 用户ID
     * @return 操作结果
     */
    @DeleteMapping("/chat/user/{userId}")
    public OperationResponse clearUserMemory(@PathVariable String userId) {
        log.debug("转发清理用户记忆请求到Service层 [用户:{}]", userId);
        return aiChatService.clearUserMemory(userId);
    }
    
    /**
     * 清理指定会话记忆
     * Controller层负责接口转发，业务逻辑由Service层处理
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/chat/user/{userId}/sessions/{sessionId}")
    public OperationResponse clearSessionMemory(@PathVariable String userId, @PathVariable String sessionId) {
        log.debug("转发清理会话记忆请求到Service层 [用户:{}, 会话:{}]", userId, sessionId);
        return aiChatService.clearSessionMemory(userId, sessionId);
    }
    
    /**
     * 获取会话统计信息
     * Controller层负责接口转发，业务逻辑由Service层处理
     * 
     * @return 统计信息响应
     */
    @GetMapping("/chat/stats")
    public StatsResponse getStats() {
        log.debug("转发获取统计信息请求到Service层");
        return aiChatService.getStats();
    }

    /**
     * 健康检查
     * Controller层负责接口转发，业务逻辑由Service层处理
     * 
     * @return 健康状态响应
     */
    @GetMapping("/health")
    public HealthResponse health() {
        log.debug("转发健康检查请求到Service层");
        return aiChatService.health();
    }

    /**
     * 服务信息
     * Controller层负责接口转发，业务逻辑由Service层处理
     * 
     * @return 服务信息响应
     */
    @GetMapping("/info")
    public ServiceInfoResponse info() {
        log.debug("转发获取服务信息请求到Service层");
        return aiChatService.info();
    }
}