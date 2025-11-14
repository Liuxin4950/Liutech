package chat.liuxin.ai.controller;

import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.resp.ChatHistoryResponse;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.MemoryService;
import chat.liuxin.ai.entity.AiChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * AI聊天控制器
 * 路由说明：
 * - POST /ai/chat        普通模式：一次性返回完整 AI 回复，并按会话落库 user/assistant 消息。
 * - POST /ai/chat/stream 流式模式：SSE 推送事件流（user/start/data/complete/error），在 complete 或 error 时入库 assistant。
 * - GET  /ai/chat/history 分页查询用户全局历史（倒序），便于回放与列表展示。
 * - DELETE /ai/chat/memory 清空用户所有聊天消息（物理删除，仅消息表）。
 *
 * 会话与消息副作用：
 * - 当 request.conversationId 为空时将创建新会话（在服务层处理），随后保存消息；否则直接使用现有会话。
 * - 每次保存消息会同步维护会话的 messageCount 与 lastMessageAt。
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
     * AI聊天接口 - 普通模式
     * 请求体：ChatRequest（包含 message、model、conversationId、context 等）
     * 返回体：ChatResponse（包含 success、message、conversationId、historyCount 等）
     * 持久化副作用：保存 user 与 assistant 消息；必要时创建会话。
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        Long userId = getCurrentUserId();
        log.info("接受到了普通模式的请求，用户ID: {}", userId);
        if (userId == null) {
            return ChatResponse.error("用户未认证");
        }
        return aiChatService.processChat(request, userId);
    }

    

    /**
     * 获取聊天历史记录接口 - 分页查询（用户维度，倒序）
     * 参数：page（>=1）、size（<=100）
     * 返回：ChatHistoryResponse（列表与总数）
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

    /** 清空用户聊天记忆接口（仅删除消息表，不影响会话表） */
    @DeleteMapping("/chat/memory")
    public ChatResponse clearChatMemory() {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ChatResponse.error("用户未认证");
            }

            String userIdStr = userId.toString();
            log.info("清空用户聊天记忆，用户ID: {}", userIdStr);

            // 调用记忆服务清空用户所有记忆
            memoryService.clearAllMemory(userIdStr);

            return ChatResponse.success("聊天记忆已清空");

        } catch (Exception e) {
            log.error("清空聊天记忆失败", e);
            return ChatResponse.error("清空聊天记忆失败: " + e.getMessage());
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
