package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.ChatSession;

import java.util.List;
import java.util.Map;

/**
 * AI聊天会话服务接口
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
public interface ChatSessionService {

    /**
     * 创建或获取会话
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param title 会话标题（可选）
     * @return 会话信息
     */
    ChatSession createOrGetSession(Long userId, String sessionId, String title);

    /**
     * 获取用户所有会话列表
     * 
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatSession> getUserSessions(Long userId);

    /**
     * 获取用户会话列表（返回Map格式，包含元数据）
     * 
     * @param userId 用户ID
     * @return 会话Map，key为sessionId，value为会话元数据
     */
    Map<String, Map<String, Object>> getUserSessionsMap(Long userId);

    /**
     * 获取指定会话信息
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 会话信息
     */
    ChatSession getSession(Long userId, String sessionId);

    /**
     * 更新会话消息数量
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param messageCount 消息数量
     */
    void updateMessageCount(Long userId, String sessionId, Integer messageCount);

    /**
     * 更新会话标题
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param title 新标题
     */
    void updateSessionTitle(Long userId, String sessionId, String title);

    /**
     * 删除指定会话
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    boolean deleteSession(Long userId, String sessionId);

    /**
     * 删除用户所有会话
     * 
     * @param userId 用户ID
     * @return 删除的会话数量
     */
    int deleteAllUserSessions(Long userId);

    /**
     * 获取统计信息
     * 
     * @return 统计信息Map
     */
    Map<String, Object> getStatistics();

    /**
     * 检查会话是否存在
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 是否存在
     */
    boolean sessionExists(Long userId, String sessionId);
}