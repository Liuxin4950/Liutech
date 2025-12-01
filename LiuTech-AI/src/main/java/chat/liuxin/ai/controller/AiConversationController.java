package chat.liuxin.ai.controller;

import chat.liuxin.ai.entity.AiConversation;
import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理接口
 * 路由说明：
 * - GET    /ai/conversations                 列出用户的会话（按 last_message_at 倒序）
 * - POST   /ai/conversations                 创建会话（type/title 可选）
 * - GET    /ai/conversations/{id}/messages   分页列出会话内消息（倒序）
 * - PUT    /ai/conversations/{id}/rename     重命名会话标题
 * - PUT    /ai/conversations/{id}/archive    归档会话（status=9）
 * - DELETE /ai/conversations/{id}            删除会话（先删消息再删会话）
 */
@Slf4j
@RestController
@RequestMapping("/ai/conversations")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AiConversationController {

    private final MemoryService memoryService;

    /** 列出当前用户的会话列表（按最后消息时间倒序） */
    @GetMapping
    public List<AiConversation> list(@RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = getCurrentUserIdStr();
        return memoryService.listConversations(userId, type, page, size);
    }

    /** 创建新会话，返回新建会话ID */
    @PostMapping
    public ChatResponse create(@RequestParam(required = false) String type,
            @RequestParam(required = false) String title) {
        String userId = getCurrentUserIdStr();
        Long id = memoryService.createConversation(userId, title != null ? title : "新会话");
        return ChatResponse.builder()
                .success(true)
                .message("会话创建成功")
                .conversationId(id)
                .build();
    }

    /** 分页列出指定会话的消息（倒序） */
    @GetMapping("/{id}/messages")
    public List<AiChatMessage> messages(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return memoryService.listMessagesByConversation(id, page, size);
    }

    /** 重命名会话标题 */
    @PutMapping("/{id}/rename")
    public ChatResponse rename(@PathVariable Long id, @RequestParam String title) {
        memoryService.renameConversation(id, title);
        return ChatResponse.success("会话重命名成功");
    }

    /** 归档会话（status=9） */
    @PutMapping("/{id}/archive")
    public ChatResponse archive(@PathVariable Long id) {
        memoryService.archiveConversation(id);
        return ChatResponse.success("会话已归档");
    }

    /** 删除会话（先删消息、再删会话） */
    @DeleteMapping("/{id}")
    public ChatResponse delete(@PathVariable Long id) {
        memoryService.deleteConversation(id);
        return ChatResponse.success("会话已删除");
    }

    private String getCurrentUserIdStr() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object details = authentication != null ? authentication.getDetails() : null;
        Long uid = (details instanceof Long) ? (Long) details : 0L;
        return uid.toString();
    }
}
