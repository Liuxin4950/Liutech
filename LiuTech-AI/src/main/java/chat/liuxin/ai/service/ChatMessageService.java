package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.ChatMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * AI聊天消息服务接口
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
public interface ChatMessageService {

    /**
     * 保存用户消息
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param content 消息内容
     * @param modelName 模型名称
     * @return 保存的消息
     */
    ChatMessage saveUserMessage(Long userId, String sessionId, String content, String modelName);

    /**
     * 保存AI助手消息
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param content 消息内容
     * @param tokensUsed 使用的token数量
     * @param modelName 模型名称
     * @return 保存的消息
     */
    ChatMessage saveAssistantMessage(Long userId, String sessionId, String content, Integer tokensUsed, String modelName);

    /**
     * 获取会话历史消息
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<ChatMessage> getSessionHistory(Long userId, String sessionId);

    /**
     * 获取会话历史消息（转换为Spring AI Message格式）
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return Spring AI Message列表
     */
    List<Message> getSessionHistoryAsMessages(Long userId, String sessionId);

    /**
     * 获取最近的N条消息用于上下文记忆
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param limit 限制数量
     * @return 消息列表
     */
    List<ChatMessage> getRecentMessages(Long userId, String sessionId, Integer limit);

    /**
     * 获取最近的N条消息（转换为Spring AI Message格式）
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param limit 限制数量
     * @return Spring AI Message列表
     */
    List<Message> getRecentMessagesAsMessages(Long userId, String sessionId, Integer limit);

    /**
     * 获取会话消息数量
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 消息数量
     */
    Integer getMessageCount(Long userId, String sessionId);

    /**
     * 删除会话所有消息
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 删除的消息数量
     */
    int deleteSessionMessages(Long userId, String sessionId);

    /**
     * 删除用户所有消息
     * 
     * @param userId 用户ID
     * @return 删除的消息数量
     */
    int deleteAllUserMessages(Long userId);

    /**
     * 清理旧消息（保留最近N条）
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param keepCount 保留数量
     * @return 删除的消息数量
     */
    int cleanOldMessages(Long userId, String sessionId, Integer keepCount);
}