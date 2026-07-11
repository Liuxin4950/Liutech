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
 * 从 AiChatServiceImpl 和 StreamingChatService 中提取的重复逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatServiceHelper {

    private final PromptService promptService;
    private final MemoryService memoryService;

    /**
     * 组装本次调用要发送给模型的完整消息序列。
     *
     * 由 {@link PromptService#assemble} 产出系统提示 + 博客上下文 + 历史记录,
     * 最后追加当前用户输入(即使为空也补一条空 UserMessage,防止 Spring AI 报错)。
     */
    public List<Message> prepareMessages(ChatRequest request, String userId, Long conversationId, boolean guestMode, boolean writingMode) {
        List<Message> messages = promptService.assemble(request, userId, conversationId, guestMode, writingMode, memoryService);
        messages.add(new UserMessage(request.getMessage() != null ? request.getMessage() : ""));
        return messages;
    }

    /**
     * 新建会话时用首条用户消息裁出会话标题:超过 10 字截断加省略号,空输入回退为"新会话"。
     */
    public String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) return "新会话";
        String trimmed = firstMessage.trim();
        return trimmed.length() > 10 ? trimmed.substring(0, 10) + "..." : trimmed;
    }

    /**
     * 登录态下若发生异常,补一条 status=3 的错误占位 assistant 消息,便于历史列表看到失败痕迹。
     * 访客模式或无会话则跳过;记录本身失败只 warn 不再抛。
     */
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
