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

@Slf4j
@RestController
@RequestMapping("/ai/conversations")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AiConversationController {

    private final MemoryService memoryService;

    @GetMapping
    public List<AiConversation> list(@RequestParam(required = false) String type,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        String userId = getCurrentUserIdStr();
        return memoryService.listConversations(userId, type, page, size);
    }

    @PostMapping
    public ChatResponse create(@RequestParam(required = false) String type,
                               @RequestParam(required = false) String title) {
        String userId = getCurrentUserIdStr();
        Long id = memoryService.createConversation(userId, type != null ? type : "general", title != null ? title : "新会话", null);
        return ChatResponse.builder()
                .success(true)
                .message("会话创建成功")
                .conversationId(id)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @GetMapping("/{id}/messages")
    public List<AiChatMessage> messages(@PathVariable Long id,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return memoryService.listMessagesByConversation(id, page, size);
    }

    @PutMapping("/{id}/rename")
    public ChatResponse rename(@PathVariable Long id, @RequestParam String title) {
        memoryService.renameConversation(id, title);
        return ChatResponse.success("会话重命名成功");
    }

    @PutMapping("/{id}/archive")
    public ChatResponse archive(@PathVariable Long id) {
        memoryService.archiveConversation(id);
        return ChatResponse.success("会话已归档");
    }

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
