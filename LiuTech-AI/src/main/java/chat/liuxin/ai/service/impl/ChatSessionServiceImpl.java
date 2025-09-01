package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.entity.ChatSession;
import chat.liuxin.ai.mapper.ChatSessionMapper;
import chat.liuxin.ai.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI聊天会话服务实现类
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;

    @Override
    @Transactional
    public ChatSession createOrGetSession(Long userId, String sessionId, String title) {
        ChatSession existingSession = chatSessionMapper.findByUserIdAndSessionId(userId, sessionId);
        
        if (existingSession != null) {
            log.debug("找到已存在的会话: userId={}, sessionId={}", userId, sessionId);
            return existingSession;
        }
        
        // 创建新会话
        ChatSession newSession = new ChatSession()
                .setSessionId(sessionId)
                .setUserId(userId)
                .setTitle(title != null ? title : "新对话")
                .setMessageCount(0)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        
        chatSessionMapper.insert(newSession);
        log.info("创建新会话: userId={}, sessionId={}, title={}", userId, sessionId, title);
        
        return newSession;
    }

    @Override
    public List<ChatSession> getUserSessions(Long userId) {
        return chatSessionMapper.findByUserId(userId);
    }

    @Override
    public Map<String, Map<String, Object>> getUserSessionsMap(Long userId) {
        List<ChatSession> sessions = getUserSessions(userId);
        Map<String, Map<String, Object>> result = new HashMap<>();
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        for (ChatSession session : sessions) {
            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put("title", session.getTitle());
            sessionInfo.put("messageCount", session.getMessageCount());
            sessionInfo.put("createdAt", session.getCreatedAt().format(formatter));
            sessionInfo.put("lastActivity", session.getUpdatedAt().format(formatter));
            
            result.put(session.getSessionId(), sessionInfo);
        }
        
        return result;
    }

    @Override
    public ChatSession getSession(Long userId, String sessionId) {
        return chatSessionMapper.findByUserIdAndSessionId(userId, sessionId);
    }

    @Override
    @Transactional
    public void updateMessageCount(Long userId, String sessionId, Integer messageCount) {
        int updated = chatSessionMapper.updateMessageCount(userId, sessionId, messageCount);
        if (updated > 0) {
            log.debug("更新会话消息数量: userId={}, sessionId={}, count={}", userId, sessionId, messageCount);
        }
    }

    @Override
    @Transactional
    public void updateSessionTitle(Long userId, String sessionId, String title) {
        ChatSession session = getSession(userId, sessionId);
        if (session != null) {
            session.setTitle(title);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
            log.info("更新会话标题: userId={}, sessionId={}, title={}", userId, sessionId, title);
        }
    }

    @Override
    @Transactional
    public boolean deleteSession(Long userId, String sessionId) {
        int deleted = chatSessionMapper.softDeleteByUserIdAndSessionId(userId, sessionId);
        if (deleted > 0) {
            log.info("删除会话: userId={}, sessionId={}", userId, sessionId);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public int deleteAllUserSessions(Long userId) {
        int deleted = chatSessionMapper.softDeleteByUserId(userId);
        log.info("删除用户所有会话: userId={}, count={}", userId, deleted);
        return deleted;
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        Long activeUsers = chatSessionMapper.countActiveUsers();
        Long activeSessions = chatSessionMapper.countActiveSessions();
        
        stats.put("activeUserCount", activeUsers != null ? activeUsers : 0L);
        stats.put("activeSessionCount", activeSessions != null ? activeSessions : 0L);
        stats.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        return stats;
    }

    @Override
    public boolean sessionExists(Long userId, String sessionId) {
        ChatSession session = getSession(userId, sessionId);
        return session != null;
    }
}