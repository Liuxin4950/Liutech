package chat.liuxin.ai.controller;

import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.ChatResponse;
import chat.liuxin.ai.dto.ChatHistoryResponse;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.MemoryService;
import chat.liuxin.ai.entity.AiChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import chat.liuxin.ai.common.utils.AuthUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * AI 聊天控制器。
 *
 * 路由说明：
 * - POST /ai/chat        看板娘聊天完整回复。
 * - POST /ai/chat/stream 看板娘聊天 SSE 回复。
 * - POST /ai/writing     写作助手完整回复。
 * - POST /ai/writing/stream 写作助手 SSE 回复。
 * - GET  /ai/chat/history 分页查询用户全局历史（倒序）。
 * - DELETE /ai/chat/memory 清空用户所有聊天消息（物理删除，仅消息表）。
 *
 * 会话与消息副作用：
 * - 当 request.conversationId 为空时将创建新会话（在服务层处理），随后保存消息；否则直接使用现有会话。
 * - 每次保存消息会同步维护会话的 messageCount 与 lastMessageAt。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@Validated
@RequiredArgsConstructor
public class AiChatController {

    private static final String LEGACY_ROUTE_HEADER = "X-LiuTech-AI-Route";
    private static final String LEGACY_CHAT_ROUTE = "legacy-chat";

    private final AiChatService aiChatService;
    private final MemoryService memoryService;
    private final AuthUtils authUtils;

    /**
     * 探活接口，返回当前认证的用户 ID（未登录返回 null）。
     * 前端用来快速判断 AI 服务通不通、token 是否被 AI 服务认可。
     */
    @GetMapping("/status")
    public String testStatus() {
        Long userId = authUtils.getCurrentUserId();
        return "服务可用，用户ID: " + userId;
    }

    /**
     * 看板娘同步聊天：一次性返回完整回复（非流式）。
     *
     * 匿名放行（SecurityConfig 里 /ai/chat 是 permitAll），登录用户走会话持久化，
     * 匿名用户走 guest 模式（不落库，靠 tempMessages 传递上下文）。
     *
     * 副作用：登录时会创建/更新会话并保存 user + assistant 两条消息。
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        markLegacyRoute(response);
        Long userId = authUtils.getCurrentUserId();
        return aiChatService.processChat(request, userId, authUtils.resolveRole());
    }

    /**
     * 看板娘流式聊天：SSE 推送。
     *
     * 事件序列：start → data* → avatar-cue* → audio*|audio-skip* → article-results? → complete → audio-complete?
     * 出错时发 error 事件后关闭连接。ttsEnabled=true 时才有 audio* 事件。
     *
     * 副作用同 {@link #chat}，另外流式中断时会把 partial 内容以 status=3 保存。
     */
    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        markLegacyRoute(response);
        Long userId = authUtils.getCurrentUserId();
        return aiChatService.processStreamChat(request, userId, authUtils.resolveRole());
    }

    /**
     * 写作助手同步：管理员专属（SecurityConfig 里限定 hasRole('ADMIN')）。
     *
     * 走 WRITING 模式，注册 WritingTools（分类/标签工具），不落库。
     * 底层客户端会以流式收集方式规避长响应下的 RestClient 超时。
     */
    @PostMapping("/writing")
    public ChatResponse writing(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        markLegacyRoute(response);
        Long userId = authUtils.getCurrentUserId();
        return aiChatService.processWriting(request, userId, authUtils.resolveRole());
    }

    /** 写作助手流式版：管理员专属。事件序列同 /chat/stream 但没有会话持久化。 */
    @PostMapping("/writing/stream")
    public SseEmitter writingStream(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        markLegacyRoute(response);
        Long userId = authUtils.getCurrentUserId();
        return aiChatService.processWritingStream(request, userId, authUtils.resolveRole());
    }

    /**
     * 分页获取当前用户的历史消息（跨会话，倒序）。
     * 供个人中心的"聊天记录"页面使用；游客未认证时返回错误响应。
     * size 上限 100，防止一次拉太多。
     */
    @GetMapping("/chat/history")
    public ChatHistoryResponse getChatHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Long userId = authUtils.getCurrentUserId();
            if (userId == null) {
                return ChatHistoryResponse.error("用户未认证");
            }

            String userIdStr = userId.toString();

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
     * 清空当前用户的所有聊天历史（物理删除消息 + 删除会话）。
     * 前端"清空聊天"按钮的目标端点。删除后无法恢复。
     */
    @DeleteMapping("/chat/memory")
    public ChatResponse clearChatMemory() {
        try {
            Long userId = authUtils.getCurrentUserId();
            if (userId == null) {
                return ChatResponse.error("用户未认证");
            }

            String userIdStr = userId.toString();

            // 调用记忆服务清空用户所有记忆
            memoryService.clearAllMemory(userIdStr);

            return ChatResponse.success("聊天记忆已清空");

        } catch (Exception e) {
            log.error("清空聊天记忆失败", e);
            return ChatResponse.error("清空聊天记忆失败: " + e.getMessage());
        }
    }

    /**
     * 打上遗留路由标记，供 nginx/日志识别老聊天路径。
     * 目前所有 /ai/* 都会打，后续如引入 v2 版本可用来区分。
     */
    private void markLegacyRoute(HttpServletResponse response) {
        if (response != null) {
            response.setHeader(LEGACY_ROUTE_HEADER, LEGACY_CHAT_ROUTE);
        }
    }
}
