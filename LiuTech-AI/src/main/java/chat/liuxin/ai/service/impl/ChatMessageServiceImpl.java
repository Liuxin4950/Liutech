package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.entity.ChatMessage;
import chat.liuxin.ai.mapper.ChatMessageMapper;
import chat.liuxin.ai.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI聊天消息服务实现类
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;

    @Override
    @Transactional
    public ChatMessage saveUserMessage(Long userId, String sessionId, String content, String modelName) {
        ChatMessage message = new ChatMessage()
                .setSessionId(sessionId)
                .setUserId(userId)
                .setRole(ChatMessage.Role.USER.getValue())
                .setContent(content)
                .setTokensUsed(0) // 用户消息不计算token
                .setModelName(modelName)
                .setCreatedAt(LocalDateTime.now());
        
        chatMessageMapper.insert(message);
        log.debug("保存用户消息: userId={}, sessionId={}, content length={}", userId, sessionId, content.length());
        
        return message;
    }

    @Override
    @Transactional
    public ChatMessage saveAssistantMessage(Long userId, String sessionId, String content, Integer tokensUsed, String modelName) {
        ChatMessage message = new ChatMessage()
                .setSessionId(sessionId)
                .setUserId(userId)
                .setRole(ChatMessage.Role.ASSISTANT.getValue())
                .setContent(content)
                .setTokensUsed(tokensUsed != null ? tokensUsed : 0)
                .setModelName(modelName)
                .setCreatedAt(LocalDateTime.now());
        
        chatMessageMapper.insert(message);
        log.debug("保存AI消息: userId={}, sessionId={}, content length={}, tokens={}", 
                userId, sessionId, content.length(), tokensUsed);
        
        return message;
    }

    @Override
    public List<ChatMessage> getSessionHistory(Long userId, String sessionId) {
        return chatMessageMapper.findByUserIdAndSessionId(userId, sessionId);
    }

    @Override
    public List<Message> getSessionHistoryAsMessages(Long userId, String sessionId) {
        List<ChatMessage> chatMessages = getSessionHistory(userId, sessionId);
        return convertToSpringAIMessages(chatMessages);
    }

    @Override
    public List<ChatMessage> getRecentMessages(Long userId, String sessionId, Integer limit) {
        return chatMessageMapper.findRecentMessages(userId, sessionId, limit);
    }

    @Override
    public List<Message> getRecentMessagesAsMessages(Long userId, String sessionId, Integer limit) {
        List<ChatMessage> chatMessages = getRecentMessages(userId, sessionId, limit);
        return convertToSpringAIMessages(chatMessages);
    }

    @Override
    public Integer getMessageCount(Long userId, String sessionId) {
        Integer count = chatMessageMapper.countByUserIdAndSessionId(userId, sessionId);
        return count != null ? count : 0;
    }

    @Override
    @Transactional
    public int deleteSessionMessages(Long userId, String sessionId) {
        int deleted = chatMessageMapper.deleteByUserIdAndSessionId(userId, sessionId);
        log.info("删除会话消息: userId={}, sessionId={}, count={}", userId, sessionId, deleted);
        return deleted;
    }

    @Override
    @Transactional
    public int deleteAllUserMessages(Long userId) {
        int deleted = chatMessageMapper.deleteByUserId(userId);
        log.info("删除用户所有消息: userId={}, count={}", userId, deleted);
        return deleted;
    }

    @Override
    @Transactional
    public int cleanOldMessages(Long userId, String sessionId, Integer keepCount) {
        if (keepCount == null || keepCount <= 0) {
            return 0;
        }
        
        List<ChatMessage> recentMessages = getRecentMessages(userId, sessionId, keepCount);
        if (recentMessages.isEmpty()) {
            return 0;
        }
        
        // 获取最早保留消息的时间
        LocalDateTime cutoffTime = recentMessages.get(recentMessages.size() - 1).getCreatedAt();
        
        // 删除早于cutoffTime的消息
        // 注意：这里需要在Mapper中实现deleteOldMessages方法
        // int deleted = chatMessageMapper.deleteOldMessages(userId, sessionId, cutoffTime);
        // log.info("清理旧消息: userId={}, sessionId={}, keepCount={}, deleted={}", userId, sessionId, keepCount, deleted);
        // return deleted;
        
        // 暂时返回0，等待Mapper方法实现
        log.warn("清理旧消息功能待实现: userId={}, sessionId={}, keepCount={}", userId, sessionId, keepCount);
        return 0;
    }

    /**
     * 将ChatMessage列表转换为Spring AI Message列表
     * 
     * @param chatMessages 聊天消息列表
     * @return Spring AI Message列表
     */
    private List<Message> convertToSpringAIMessages(List<ChatMessage> chatMessages) {
        List<Message> messages = new ArrayList<>();
        
        for (ChatMessage chatMessage : chatMessages) {
            Message message;
            if (ChatMessage.Role.USER.getValue().equals(chatMessage.getRole())) {
                message = new UserMessage(chatMessage.getContent());
            } else {
                message = new AssistantMessage(chatMessage.getContent());
            }
            messages.add(message);
        }
        
        return messages;
    }
}