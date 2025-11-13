package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.entity.AiChatMessage;
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
 * 时间：2025-09-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final AiChatMessageMapper messageMapper;
    private final chat.liuxin.ai.mapper.AiConversationMapper conversationMapper;

    /**
     * 按用户ID查询最近N条消息（按创建时间升序）
     */
    @Override
    public List<AiChatMessage> listRecentMessages(String userId, int limit) {
        if (limit <= 0) return Collections.emptyList();
        // 1) 按创建时间倒序查询最近N条；同秒内用 id 作为稳定的二级排序，避免先存的user落到assistant之后
        List<AiChatMessage> desc = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getUserId, userId)
                .orderByDesc(AiChatMessage::getCreatedAt)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + limit)
        );
        // 2) 反转为升序（从旧到新）
        Collections.reverse(desc);
        return desc;
    }

    /**
     * 分页查询某用户的聊天历史记录（按创建时间倒序）
     */
    @Override
    public List<AiChatMessage> listHistoryMessages(String userId, int page, int size) {
        if (page < 1 || size <= 0) return Collections.emptyList();
        int offset = (page - 1) * size;
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getUserId, userId)
                .orderByDesc(AiChatMessage::getCreatedAt)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + offset + ", " + size)
        );
    }

    /**
     * 查询某用户的聊天历史记录总数
     */
    @Override
    public long countHistoryMessages(String userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getUserId, userId)
        );
    }

    /**
     * 保存一条用户消息（role=user，status固定1）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(String userId, Long conversationId, String content, String model, String metadataJson) {
        AiChatMessage m = new AiChatMessage();
        m.setUserId(userId);
        m.setConversationId(conversationId);
        m.setRole("user");
        m.setContent(content);
        m.setModel(model);
        m.setStatus(1);
        m.setMetadata(metadataJson);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        if (conversationId != null) {
            var c = conversationMapper.selectById(conversationId);
            if (c != null) {
                c.setMessageCount((c.getMessageCount() == null ? 0 : c.getMessageCount()) + 1);
                c.setLastMessageAt(LocalDateTime.now());
                conversationMapper.updateById(c);
            }
        }
    }

    /**
     * 保存一条AI消息（role=assistant，status根据实现分成功/错误）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAssistantMessage(String userId, Long conversationId, String content, String model, int status, String metadataJson) {
        AiChatMessage m = new AiChatMessage();
        m.setUserId(userId);
        m.setConversationId(conversationId);
        m.setRole("assistant");
        m.setContent(content);
        m.setModel(model);
        m.setStatus(status);
        m.setMetadata(metadataJson);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        if (conversationId != null) {
            var c = conversationMapper.selectById(conversationId);
            if (c != null) {
                c.setMessageCount((c.getMessageCount() == null ? 0 : c.getMessageCount()) + 1);
                c.setLastMessageAt(LocalDateTime.now());
                conversationMapper.updateById(c);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanupByRetainLastN(String userId, int retainLastN) {
        if (retainLastN <= 0) return;
        // 删除该用户除最后N条之外的其他记录
        // SQL思路：找出第N条的created_at边界，再删除更早的记录
        // 这里用子查询 + <= 边界的方式实现（不同数据库略有差异，MySQL 8 OK）
        // String sql = "DELETE FROM ai_chat_message " +
        //         "WHERE user_id = #{userId} AND id NOT IN (" +
        //         "  SELECT id FROM (SELECT id FROM ai_chat_message WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{retainLastN}) t" +
        //         ")";
        // MyBatis-Plus不支持直接写上面的原生SQL在Service，这里退一步：
        // 方案B：查询第N条的时间边界，再按时间删除更早的（两步法）。
        List<AiChatMessage> desc = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getUserId, userId)
                .orderByDesc(AiChatMessage::getCreatedAt)
                .last("LIMIT " + retainLastN + ",1") // 从第retainLastN条开始的第1条（即第N+1条）
        );
        if (desc == null || desc.isEmpty()) return; // 不足N条，无需清理
        java.time.LocalDateTime boundary = desc.get(0).getCreatedAt();
        int deleted = messageMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getUserId, userId)
                .lt(AiChatMessage::getCreatedAt, boundary)
        );
        if (deleted > 0) {
            log.info("记忆清理：userId={}, 删除{}条，保留最近{}条", userId, deleted, retainLastN);
        }
    }

    /**
     * 清空用户所有聊天记忆（物理删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearAllMemory(String userId) {
        int deleted = messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getUserId, userId)
        );
        log.info("清空用户记忆：userId={}, 删除{}条记录", userId, deleted);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createConversation(String userId, String type, String title, String metadataJson) {
        chat.liuxin.ai.entity.AiConversation c = new chat.liuxin.ai.entity.AiConversation();
        c.setUserId(userId);
        c.setType(type);
        c.setTitle(title);
        c.setStatus(0);
        c.setMessageCount(0);
        c.setMetadata(metadataJson);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(c);
        return c.getId();
    }

    @Override
    public java.util.List<chat.liuxin.ai.entity.AiConversation> listConversations(String userId, String type, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<chat.liuxin.ai.entity.AiConversation>()
                .select(chat.liuxin.ai.entity.AiConversation::getId,
                        chat.liuxin.ai.entity.AiConversation::getUserId,
                        chat.liuxin.ai.entity.AiConversation::getType,
                        chat.liuxin.ai.entity.AiConversation::getTitle,
                        chat.liuxin.ai.entity.AiConversation::getStatus,
                        chat.liuxin.ai.entity.AiConversation::getMessageCount,
                        chat.liuxin.ai.entity.AiConversation::getLastMessageAt)
                .eq(chat.liuxin.ai.entity.AiConversation::getUserId, userId)
                .eq(type != null && !type.isEmpty(), chat.liuxin.ai.entity.AiConversation::getType, type)
                .orderByDesc(chat.liuxin.ai.entity.AiConversation::getLastMessageAt)
                .orderByDesc(chat.liuxin.ai.entity.AiConversation::getId)
                .last("LIMIT " + offset + ", " + size);
        return conversationMapper.selectList(qw);
    }

    @Override
    public chat.liuxin.ai.entity.AiConversation getConversation(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    @Override
    public java.util.List<AiChatMessage> listMessagesByConversation(Long conversationId, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .select(AiChatMessage::getId,
                        AiChatMessage::getRole,
                        AiChatMessage::getContent,
                        AiChatMessage::getCreatedAt)
                .eq(AiChatMessage::getConversationId, conversationId)
                .orderByDesc(AiChatMessage::getCreatedAt)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + offset + ", " + size)
        );
    }

    @Override
    public java.util.List<AiChatMessage> listLastMessagesByConversation(Long conversationId, int limit) {
        if (limit <= 0) return java.util.Collections.emptyList();
        java.util.List<AiChatMessage> desc = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getConversationId, conversationId)
                .orderByDesc(AiChatMessage::getCreatedAt)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + limit)
        );
        java.util.Collections.reverse(desc);
        return desc;
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveConversation(Long conversationId) {
        var c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setStatus(9);
            c.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(c);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long conversationId) {
        // 先删除该会话的消息，再删除会话
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getConversationId, conversationId)
        );
        conversationMapper.deleteById(conversationId);
    }
}
