package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 聊天服务公共工具方法。
 *
 * <p>从 AiChatServiceImpl 和 StreamingChatService 中提取的重复逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatServiceHelper {

    private final PromptService promptService;
    private final MemoryService memoryService;

    public List<Message> prepareMessages(ChatRequest request, String userId, Long conversationId, boolean guestMode) {
        List<Message> messages = promptService.assemble(request, userId, conversationId, guestMode, memoryService);
        messages.add(new UserMessage(request.getMessage() != null ? request.getMessage() : ""));
        return messages;
    }

    public String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) return "新会话";
        String trimmed = firstMessage.trim();
        return trimmed.length() > 10 ? trimmed.substring(0, 10) + "..." : trimmed;
    }

    public void saveErrorIfNeeded(boolean guestMode, String userId, Long conversationId, String modelName) {
        if (!guestMode && conversationId != null) {
            try {
                memoryService.saveAssistantMessage(userId, conversationId, null, modelName, 3, null);
            } catch (Exception e) {
                log.warn("记录错误消息失败: {}", e.getMessage());
            }
        }
    }
}
