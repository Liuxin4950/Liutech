package chat.liuxin.ai.controller;

import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.resp.ChatHistoryResponse;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.MemoryService;
import chat.liuxin.ai.entity.AiChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import java.util.List;

/**
 * AI聊天控制器（精简版）
 * 学习顺序：
 * 1) 读 /chat 普通模式
 * 2) 读 /chat/stream 流式模式
 *
 * 作者：刘鑫
 * 时间：2025-09-05
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = "*")
@Validated
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final MemoryService memoryService;

    /**
     * 测试服务是否可用
     */
    @GetMapping("/status")
    public String testStatus() {
        Long userId = getCurrentUserId();
        log.info("测试服务是否可用，用户ID: {}", userId);
        return "服务可用，用户ID: " + userId;
    }

    /**
     * 1) AI聊天接口 - 普通模式
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        log.info("=== AI聊天控制器被调用 ===");
        Long userId = getCurrentUserId();
        log.info("接受到了普通模式的请求，用户ID: {}", userId);
        if (userId == null) {
            return ChatResponse.error("用户未认证");
        }
        return aiChatService.processChat(request, userId);
    }

    /**
     * 2) AI聊天接口 - 流式输出模式（SSE）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        Long userId = getCurrentUserId();
        log.debug("接受到了流式模式的请求，用户ID: {}", userId);
        return aiChatService.processChatStream(request, userId);
    }

    /**
     * 3) 获取聊天历史记录接口 - 分页查询
     */
    @GetMapping("/chat/history")
    public ChatHistoryResponse getChatHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ChatHistoryResponse.error("用户未认证");
            }
            
            String userIdStr = userId.toString();
            log.info("获取聊天历史记录，用户ID: {}, 页码: {}, 每页大小: {}", userIdStr, page, size);
            
            // 参数校验
            if (page < 1) page = 1;
            if (size < 1) size = 20;
            if (size > 100) size = 100; // 限制最大每页数量
            
            // 查询历史记录和总数
            List<AiChatMessage> messages = memoryService.listHistoryMessages(userIdStr, page, size);
            long total = memoryService.countHistoryMessages(userIdStr);
            
            return ChatHistoryResponse.success(messages, page, size, total, userIdStr);
            
        } catch (Exception e) {
            log.error("获取聊天历史记录失败", e);
            return ChatHistoryResponse.error("获取聊天历史记录失败: " + e.getMessage());
        }
    }

    /**
     * 从JWT认证上下文中获取当前用户ID
     * @return 用户ID，如果未认证则返回null
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("认证对象: {}", authentication);
        if (authentication != null) {
            log.info("认证状态: {}, 主体: {}, 详情: {}", 
                authentication.isAuthenticated(), 
                authentication.getPrincipal(), 
                authentication.getDetails());
            if (authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
                Object details = authentication.getDetails();
                if (details instanceof Long) {
                    return (Long) details;
                }
            }
        }
        log.warn("无法获取当前用户ID，可能未正确认证");
        return null;
    }
}