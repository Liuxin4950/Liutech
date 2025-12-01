package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.entity.AiConversation;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.service.MemoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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
     * 查询策略：先按 created_at、id 倒序抓取 N 条，再反转为升序。
     * 注意：新表结构中移除了user_id字段，需要通过conversation关联查询
     */
    @Override
    public List<AiChatMessage> listRecentMessages(String userId, int limit) {
        if (limit <= 0) return Collections.emptyList();
        
        // 1) 先查询用户的所有会话ID
        List<Long> conversationIds = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .select(AiConversation::getId)
        ).stream().map(AiConversation::getId).toList();
        
        if (conversationIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 2) 按创建时间倒序查询最近N条消息；同秒内用 id 作为稳定的二级排序
        List<AiChatMessage> desc = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .in(AiChatMessage::getConversationId, conversationIds)
                .orderByDesc(AiChatMessage::getCreatedAt)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + limit)
        );
        
        // 3) 反转为升序（从旧到新）
        Collections.reverse(desc);
        return desc;
    }

    /**
     * 分页查询某用户的聊天历史记录（按 created_at 与 id 倒序）。
     * 注意：新表结构中移除了user_id字段，需要通过conversation关联查询
     */
    @Override
    public List<AiChatMessage> listHistoryMessages(String userId, int page, int size) {
        if (page < 1 || size <= 0) return Collections.emptyList();
        
        // 1) 先查询用户的所有会话ID
        List<Long> conversationIds = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .select(AiConversation::getId)
        ).stream().map(AiConversation::getId).toList();
        
        if (conversationIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 2) 分页查询消息
        int offset = (page - 1) * size;
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .in(AiChatMessage::getConversationId, conversationIds)
                .orderByDesc(AiChatMessage::getCreatedAt)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + offset + ", " + size)
        );
    }

    /** 查询某用户的聊天历史记录总数 */
    @Override
    public long countHistoryMessages(String userId) {
        // 1) 先查询用户的所有会话ID
        List<Long> conversationIds = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .select(AiConversation::getId)
        ).stream().map(AiConversation::getId).toList();
        
        if (conversationIds.isEmpty()) {
            return 0;
        }
        
        // 2) 统计消息总数
        return messageMapper.selectCount(new LambdaQueryWrapper<AiChatMessage>()
                .in(AiChatMessage::getConversationId, conversationIds)
        );
    }

    /** 
     * 保存一条用户消息（role=user，status 固定 1）
     * 重构说明：移除了metadata字段，简化存储
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
        
        // 1) 先查询用户的所有会话ID
        List<Long> conversationIds = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .select(AiConversation::getId)
        ).stream().map(AiConversation::getId).toList();
        
        if (conversationIds.isEmpty()) {
            return;
        }
        
        // 2) 查询第N+1条消息作为边界
        List<AiChatMessage> desc = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .in(AiChatMessage::getConversationId, conversationIds)
                .orderByDesc(AiChatMessage::getCreatedAt)
                .last("LIMIT " + retainLastN + ",1") // 从第retainLastN条开始的第1条（即第N+1条）
        );
        
        if (desc == null || desc.isEmpty()) return; // 不足N条，无需清理
        
        java.time.LocalDateTime boundary = desc.get(0).getCreatedAt();
        int deleted = messageMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatMessage>()
                .in(AiChatMessage::getConversationId, conversationIds)
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
        // 1) 先查询用户的所有会话ID
        List<Long> conversationIds = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .select(AiConversation::getId)
        ).stream().map(AiConversation::getId).toList();
        
        if (conversationIds.isEmpty()) {
            return;
        }
        
        // 2) 删除所有消息
        int deleted = messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .in(AiChatMessage::getConversationId, conversationIds)
        );
        
        // 3) 删除所有会话
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