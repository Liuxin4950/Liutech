package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.entity.AiConversation;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.service.MemoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 记忆服务实现类
 * 作者：刘鑫
 * 时间：2025-12-01
 * 重构说明：适配新的数据库表结构，简化设计，优化性能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final AiChatMessageMapper messageMapper;
    private final chat.liuxin.ai.mapper.AiConversationMapper conversationMapper;

    /**
     * 按用户 ID 查询最近 N 条消息（最终返回升序），用于提示词上下文拼接。
     * 优化说明：使用JOIN查询一次数据库操作，避免N+1查询问题
     * 注意：聊天系统实时性要求高，不适合使用缓存
     */
    @Override
    public List<AiChatMessage> listRecentMessages(String userId, int limit) {
        if (limit <= 0) return Collections.emptyList();
        
        // 优化：直接使用JOIN查询，一次数据库操作获取结果
        return messageMapper.selectRecentMessagesByUserId(userId, limit);
    }

    /**
     * 分页查询某用户的聊天历史记录（按 created_at 与 id 倒序）。
     * 优化说明：使用JOIN查询一次数据库操作，避免N+1查询问题
     * 注意：聊天系统实时性要求高，不适合使用缓存
     */
    @Override
    public List<AiChatMessage> listHistoryMessages(String userId, int page, int size) {
        if (page < 1 || size <= 0) return Collections.emptyList();
        
        // 优化：直接使用JOIN查询，一次数据库操作获取结果
        int offset = (page - 1) * size;
        return messageMapper.selectHistoryMessagesByUserId(userId, offset, size);
    }

    /** 查询某用户的聊天历史记录总数 */
    @Override
    public long countHistoryMessages(String userId) {
        // 优化：直接使用JOIN查询，一次数据库操作获取结果
        return messageMapper.countMessagesByUserId(userId);
    }

    /** 
     * 保存一条用户消息（role=user，status 固定 1）
     * 重构说明：移除了metadata字段，简化存储
     * 注意：聊天系统实时性要求高，不适合使用缓存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(String userId, Long conversationId, String content, String model, String metadataJson) {
        // 获取当前会话的最大序号
        Integer maxSeqNo = getMaxSeqNo(conversationId);
        
        AiChatMessage m = new AiChatMessage();
        m.setConversationId(conversationId);
        m.setRole("user");
        m.setContent(content);
        m.setModel(model);
        m.setStatus(1);
        m.setSeqNo(maxSeqNo + 1);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }

    /** 
     * 保存一条 AI 消息（role=assistant；status=1/9）
     * 重构说明：移除了metadata字段，简化存储
     * 注意：聊天系统实时性要求高，不适合使用缓存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAssistantMessage(String userId, Long conversationId, String content, String model, int status, String metadataJson) {
        // 获取当前会话的最大序号
        Integer maxSeqNo = getMaxSeqNo(conversationId);
        
        AiChatMessage m = new AiChatMessage();
        m.setConversationId(conversationId);
        m.setRole("assistant");
        m.setContent(content);
        m.setModel(model);
        m.setStatus(status);
        m.setSeqNo(maxSeqNo + 1);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }

    /**
     * 获取指定会话的最大消息序号
     * @param conversationId 会话ID
     * @return 最大序号，如果没有消息则返回0
     */
    private Integer getMaxSeqNo(Long conversationId) {
        AiChatMessage lastMessage = messageMapper.selectOne(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getConversationId, conversationId)
                .select(AiChatMessage::getSeqNo)
                .orderByDesc(AiChatMessage::getSeqNo)
                .last("LIMIT 1")
        );
        return lastMessage != null ? lastMessage.getSeqNo() : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanupByRetainLastN(String userId, int retainLastN) {
        if (retainLastN <= 0) return;
        
        // 优化：使用JOIN查询一次数据库操作确定边界时间
        List<AiChatMessage> boundaryMessages = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .in(AiChatMessage::getConversationId, 
                    conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                            .eq(AiConversation::getUserId, userId)
                            .select(AiConversation::getId))
                    .stream().map(AiConversation::getId).toList())
                .orderByDesc(AiChatMessage::getCreatedAt)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + retainLastN + ", 1")
        );
        
        if (boundaryMessages == null || boundaryMessages.isEmpty()) {
            return; // 不足N条，无需清理
        }
        
        LocalDateTime boundary = boundaryMessages.get(0).getCreatedAt();
        
        // 优化：使用JOIN查询删除旧消息
        int deleted = messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .inSql(AiChatMessage::getConversationId, 
                       "SELECT id FROM ai_conversation WHERE user_id = '" + userId + "'")
                .lt(AiChatMessage::getCreatedAt, boundary)
        );
        
        if (deleted > 0) {
            log.info("记忆清理：userId={}, 删除{}条，保留最近{}条", userId, deleted, retainLastN);
        }
    }

    /** 清空用户所有聊天记忆（物理删除） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearAllMemory(String userId) {
        // 优化：使用子查询删除，减少数据库往返次数
        
        // 1) 删除所有消息（使用子查询）
        int deleted = messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .inSql(AiChatMessage::getConversationId, 
                       "SELECT id FROM ai_conversation WHERE user_id = '" + userId + "'")
        );
        
        // 2) 删除所有会话
        conversationMapper.delete(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
        );
        
        log.info("清空用户记忆：userId={}, 删除{}条记录", userId, deleted);
    }

    /**
     * 创建会话
     * 重构说明：移除了type、status、metadata字段，简化创建逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createConversation(String userId, String title) {
        AiConversation c = new AiConversation();
        LocalDateTime time = LocalDateTime.now();
        c.setUserId(userId);
        c.setTitle(title);
        c.setCreatedAt(time);
        c.setUpdatedAt(time);
        conversationMapper.insert(c);
        return c.getId();
    }

    /**
     * 查询用户会话列表
     * 重构说明：移除了type、status、messageCount、lastMessageAt字段，简化查询逻辑
     */
    @Override
    public List<AiConversation> listConversations(String userId, String type, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        var qw = new LambdaQueryWrapper<AiConversation>()
                .select(AiConversation::getId,
                        AiConversation::getUserId,
                        AiConversation::getTitle,
                        AiConversation::getCreatedAt,
                        AiConversation::getUpdatedAt)
                .eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getUpdatedAt)
                .orderByDesc(AiConversation::getId)
                .last("LIMIT " + offset + ", " + size);
        return conversationMapper.selectList(qw);
    }

    @Override
    public AiConversation getConversation(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    /**
     * 查询会话消息列表
     * 重构说明：移除了user_id字段，简化查询
     */
    @Override
    public List<AiChatMessage> listMessagesByConversation(Long conversationId, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .select(AiChatMessage::getId,
                        AiChatMessage::getRole,
                        AiChatMessage::getContent,
                        AiChatMessage::getCreatedAt,
                        AiChatMessage::getSeqNo)
                .eq(AiChatMessage::getConversationId, conversationId)
                .orderByDesc(AiChatMessage::getSeqNo)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + offset + ", " + size)
        );
    }

    @Override
    public List<AiChatMessage> listLastMessagesByConversation(Long conversationId, int limit) {
        if (limit <= 0) return java.util.Collections.emptyList();
        java.util.List<AiChatMessage> desc = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getConversationId, conversationId)
                .orderByDesc(AiChatMessage::getSeqNo)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + limit)
        );
        java.util.Collections.reverse(desc);
        return desc;
    }
    
    @Override
    public List<Message> listLastMessagesAsPromptMessages(Long conversationId, int limit) {
        if (limit <= 0) return new ArrayList<>();
        
        List<AiChatMessage> messages = listLastMessagesByConversation(conversationId, limit);
        
        return messages.stream().map(m -> {
            String role = Optional.ofNullable(m.getRole()).orElse("user");
            String content = Optional.ofNullable(m.getContent()).orElse("");
            switch (role) {
                case "system": return new SystemMessage(content);
                case "assistant": return new AssistantMessage(content);
                case "user":
                default: return new UserMessage(content);
            }
        }).collect(Collectors.toList());
    }

    /**
     * 重命名会话
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameConversation(Long conversationId, String title) {
        var c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setTitle(title);
            c.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(c);
        }
    }

    /**
     * 归档会话
     * 重构说明：移除了status字段，简化为直接删除会话
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveConversation(Long conversationId) {
        // 新表结构中没有status字段，直接删除会话
        deleteConversation(conversationId);
    }

    /**
     * 删除会话
     * 重构说明：利用外键约束自动删除消息，简化删除逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long conversationId) {
        // 新表结构中设置了外键约束 ON DELETE CASCADE，删除会话会自动删除相关消息
        conversationMapper.deleteById(conversationId);
    }
}