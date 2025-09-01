package chat.liuxin.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI聊天记忆服务 - 支持多会话版本
 * 基于用户ID+会话ID管理聊天历史记录，支持用户多会话并行
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Slf4j
@Service
public class ChatMemoryService {

    /**
     * 会话记忆存储 - 使用ConcurrentHashMap保证线程安全
     * Key: sessionKey(userId:sessionId), Value: 消息历史列表
     */
    private final Map<String, List<Message>> sessionMemory = new ConcurrentHashMap<>();
    
    /**
     * 用户会话映射 - 记录每个用户的所有会话ID
     * Key: userId, Value: Set<sessionId>
     */
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    
    /**
     * 最大历史记录数量（防止内存溢出）
     */
    private static final int MAX_HISTORY_SIZE = 20;

    /**
     * 添加用户消息到记忆
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（可为null，使用默认会话）
     * @param userMessage 用户消息内容
     */
    public void addUserMessage(String userId, String sessionId, String userMessage) {
        if (userId == null || userMessage == null) {
            log.warn("用户ID或用户消息为空，跳过记忆存储");
            return;
        }
        
        String actualSessionId = getOrCreateSessionId(userId, sessionId);
        String sessionKey = buildSessionKey(userId, actualSessionId);
        
        List<Message> messages = getOrCreateSessionMemory(sessionKey);
        messages.add(new UserMessage(userMessage));
        
        // 限制历史记录数量
        limitHistorySize(messages);
        
        log.debug("用户消息已添加到会话记忆 [{}:{}]: {}", userId, actualSessionId, userMessage);
    }

    /**
     * 添加AI助手消息到记忆
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（可为null，使用默认会话）
     * @param assistantMessage AI回复内容
     */
    public void addAssistantMessage(String userId, String sessionId, String assistantMessage) {
        if (userId == null || assistantMessage == null) {
            log.warn("用户ID或AI消息为空，跳过记忆存储");
            return;
        }
        
        String actualSessionId = getOrCreateSessionId(userId, sessionId);
        String sessionKey = buildSessionKey(userId, actualSessionId);
        
        List<Message> messages = getOrCreateSessionMemory(sessionKey);
        messages.add(new AssistantMessage(assistantMessage));
        
        // 限制历史记录数量
        limitHistorySize(messages);
        
        log.debug("AI消息已添加到会话记忆 [{}:{}]: {}", userId, actualSessionId, assistantMessage);
    }

    /**
     * 获取指定会话的所有历史消息
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（可为null，获取默认会话）
     * @return 消息历史列表
     */
    public List<Message> getUserMessages(String userId, String sessionId) {
        if (userId == null) {
            log.warn("用户ID为空，返回空消息列表");
            return new ArrayList<>();
        }
        
        String actualSessionId = getOrCreateSessionId(userId, sessionId);
        String sessionKey = buildSessionKey(userId, actualSessionId);
        
        List<Message> messages = sessionMemory.get(sessionKey);
        return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    /**
     * 清理指定会话的记忆
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（可为null，清理默认会话）
     */
    public void clearSessionMemory(String userId, String sessionId) {
        if (userId == null) {
            return;
        }
        
        String actualSessionId = sessionId != null ? sessionId : "default";
        String sessionKey = buildSessionKey(userId, actualSessionId);
        
        sessionMemory.remove(sessionKey);
        
        // 从用户会话映射中移除
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(actualSessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        
        log.info("用户 [{}] 会话 [{}] 记忆已清理", userId, actualSessionId);
    }
    
    /**
     * 清理指定用户的所有会话记忆
     * 
     * @param userId 用户ID
     */
    public void clearAllUserMemory(String userId) {
        if (userId == null) {
            return;
        }
        
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            // 清理所有会话记忆
            for (String sessionId : sessions) {
                String sessionKey = buildSessionKey(userId, sessionId);
                sessionMemory.remove(sessionKey);
            }
            // 清理用户会话映射
            userSessions.remove(userId);
            
            log.info("用户 [{}] 所有会话记忆已清理，共 {} 个会话", userId, sessions.size());
        }
    }

    /**
     * 获取当前活跃用户数量
     * 
     * @return 活跃用户数
     */
    public int getActiveUserCount() {
        return userSessions.size();
    }
    
    /**
     * 获取当前活跃会话总数
     * 
     * @return 活跃会话数
     */
    public int getActiveSessionCount() {
        return sessionMemory.size();
    }
    
    /**
     * 获取用户的所有会话ID列表
     * 
     * @param userId 用户ID
     * @return 会话ID集合
     */
    public Set<String> getUserSessions(String userId) {
        if (userId == null) {
            return new HashSet<>();
        }
        
        Set<String> sessions = userSessions.get(userId);
        return sessions != null ? new HashSet<>(sessions) : new HashSet<>();
    }
    
    /**
     * 获取会话的消息数量
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 消息数量
     */
    public int getSessionMessageCount(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return 0;
        }
        
        String sessionKey = buildSessionKey(userId, sessionId);
        List<Message> messages = sessionMemory.get(sessionKey);
        return messages != null ? messages.size() : 0;
    }
    
    /**
     * 获取会话的最后一条消息内容
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 最后一条消息内容，如果没有消息则返回null
     */
    public String getLastMessage(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return null;
        }
        
        String sessionKey = buildSessionKey(userId, sessionId);
        List<Message> messages = sessionMemory.get(sessionKey);
        
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        
        Message lastMessage = messages.get(messages.size() - 1);
        String content = lastMessage.getContent();
        
        // 如果消息太长，截取前50个字符
        if (content != null && content.length() > 50) {
            return content.substring(0, 50) + "...";
        }
        
        return content;
    }

    /**
     * 获取或创建会话ID
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（可为null）
     * @return 实际使用的会话ID
     */
    private String getOrCreateSessionId(String userId, String sessionId) {
        String actualSessionId = sessionId != null ? sessionId : "default";
        
        // 记录用户会话映射
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(actualSessionId);
        
        return actualSessionId;
    }
    
    /**
     * 构建会话键
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 会话键
     */
    private String buildSessionKey(String userId, String sessionId) {
        return userId + ":" + sessionId;
    }
    
    /**
     * 获取或创建会话记忆
     * 
     * @param sessionKey 会话键
     * @return 消息列表
     */
    private List<Message> getOrCreateSessionMemory(String sessionKey) {
        return sessionMemory.computeIfAbsent(sessionKey, k -> {
            log.info("创建新会话记忆: {}", sessionKey);
            return new ArrayList<>();
        });
    }

    /**
     * 限制历史记录数量
     * 
     * @param messages 消息列表
     */
    private void limitHistorySize(List<Message> messages) {
        while (messages.size() > MAX_HISTORY_SIZE) {
            messages.remove(0); // 移除最早的消息
            log.debug("历史记录超出限制，移除最早消息");
        }
    }


}