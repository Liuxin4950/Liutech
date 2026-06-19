package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.entity.AiConversation;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 记忆服务。
 *
 * <p>管理聊天会话与消息的持久化，包括会话 CRUD、消息保存与查询、历史清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final AiChatMessageMapper messageMapper;
    private final AiConversationMapper conversationMapper;

    /**
     * 按用户 ID 查询最近 N 条消息（最终返回升序），用于提示词上下文拼接。
     */
    public List<AiChatMessage> listRecentMessages(String userId, int limit) {
        if (limit <= 0) return Collections.emptyList();
        return messageMapper.selectRecentMessagesByUserId(userId, limit);
    }

    /**
     * 分页查询某用户的聊天历史记录（按 created_at 与 id 倒序）。
     */
    public List<AiChatMessage> listHistoryMessages(String userId, int page, int size) {
        if (page < 1 || size <= 0) return Collections.emptyList();
        int offset = (page - 1) * size;
        return messageMapper.selectHistoryMessagesByUserId(userId, offset, size);
    }

    /** 查询某用户的聊天历史记录总数 */
    public long countHistoryMessages(String userId) {
        return messageMapper.countMessagesByUserId(userId);
    }

    /** 保存一条用户消息（role=user，status 固定 1） */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(String userId, Long conversationId, String content, String model, String metadataJson) {
        Integer maxSeqNo = getMaxSeqNo(conversationId);

        AiChatMessage m = new AiChatMessage();
        m.setUserId(userId);
        m.setConversationId(conversationId);
        m.setRole("user");
        m.setContent(content);
        m.setModel(model);
        m.setStatus(1);
        m.setSeqNo(maxSeqNo + 1);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }

    /** 保存一条 AI 消息（role=assistant；status=1完成/3异常） */
    @Transactional(rollbackFor = Exception.class)
    public void saveAssistantMessage(String userId, Long conversationId, String content, String model, int status, String metadataJson) {
        Integer maxSeqNo = getMaxSeqNo(conversationId);

        AiChatMessage m = new AiChatMessage();
        m.setUserId(userId);
        m.setConversationId(conversationId);
        m.setRole("assistant");
        m.setContent(content);
        m.setModel(model);
        m.setStatus(status);
        m.setSeqNo(maxSeqNo + 1);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }

    private Integer getMaxSeqNo(Long conversationId) {
        AiChatMessage lastMessage = messageMapper.selectOne(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getConversationId, conversationId)
                .select(AiChatMessage::getSeqNo)
                .orderByDesc(AiChatMessage::getSeqNo)
                .last("LIMIT 1")
        );
        return lastMessage != null ? lastMessage.getSeqNo() : 0;
    }

    /** 清空用户所有聊天记忆（物理删除） */
    @Transactional(rollbackFor = Exception.class)
    public void clearAllMemory(String userId) {
        List<Long> conversationIds = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .select(AiConversation::getId))
                .stream().map(AiConversation::getId).toList();

        int deleted = 0;
        if (!conversationIds.isEmpty()) {
            deleted = messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                    .in(AiChatMessage::getConversationId, conversationIds)
            );
        }

        conversationMapper.delete(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
        );

        log.info("清空用户记忆：userId={}, 删除{}条记录", userId, deleted);
    }

    /** 创建会话 */
    @Transactional(rollbackFor = Exception.class)
    public Long createConversation(String userId, String title) {
        AiConversation c = new AiConversation();
        LocalDateTime time = LocalDateTime.now();
        c.setUserId(userId);
        c.setTitle(title);
        c.setStatus(0);
        c.setMessageCount(0);
        c.setCreatedAt(time);
        c.setUpdatedAt(time);
        conversationMapper.insert(c);
        return c.getId();
    }

    /** 查询用户会话列表（排除已归档的会话） */
    public List<AiConversation> listConversations(String userId, String type, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        int safeOffset = Math.max(0, offset);
        int safeSize = Math.max(1, Math.min(size, 100));
        var qw = new LambdaQueryWrapper<AiConversation>()
                .select(AiConversation::getId,
                        AiConversation::getUserId,
                        AiConversation::getTitle,
                        AiConversation::getCreatedAt,
                        AiConversation::getUpdatedAt,
                        AiConversation::getStatus,
                        AiConversation::getMessageCount,
                        AiConversation::getLastMessageAt)
                .eq(AiConversation::getUserId, userId)
                .ne(AiConversation::getStatus, 9)
                .orderByDesc(AiConversation::getUpdatedAt)
                .orderByDesc(AiConversation::getId)
                .last(false, "LIMIT " + safeOffset + ", " + safeSize);
        return conversationMapper.selectList(qw);
    }

    public AiConversation getConversation(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    public AiConversation getConversationOwnedByUser(String userId, Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在或已删除");
        }
        if (userId == null || !userId.equals(conversation.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该会话");
        }
        return conversation;
    }

    /** 分页列出会话内的消息（升序） */
    public List<AiChatMessage> listMessagesByConversation(String userId, Long conversationId, int page, int size) {
        getConversationOwnedByUser(userId, conversationId);
        int offset = Math.max(0, (page - 1) * size);
        int safeOffset = Math.max(0, offset);
        int safeSize = Math.max(1, Math.min(size, 100));
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .select(AiChatMessage::getId,
                        AiChatMessage::getRole,
                        AiChatMessage::getContent,
                        AiChatMessage::getCreatedAt,
                        AiChatMessage::getSeqNo)
                .eq(AiChatMessage::getConversationId, conversationId)
                .orderByAsc(AiChatMessage::getSeqNo)
                .orderByAsc(AiChatMessage::getId)
                .last(false, "LIMIT " + safeOffset + ", " + safeSize)
        );
    }

    /** 列出会话内最近 N 条消息（返回升序） */
    public List<AiChatMessage> listLastMessagesByConversation(String userId, Long conversationId, int limit) {
        if (limit <= 0) return Collections.emptyList();
        getConversationOwnedByUser(userId, conversationId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<AiChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getConversationId, conversationId)
                .orderByDesc(AiChatMessage::getSeqNo)
                .orderByDesc(AiChatMessage::getId)
                .last(false, "LIMIT " + safeLimit)
        );
        Collections.reverse(messages);
        return messages;
    }

    /** 获取会话内最近 N 条消息并转换为 Spring AI Message 列表（返回升序） */
    public List<Message> listLastMessagesAsPromptMessages(String userId, Long conversationId, int limit) {
        if (limit <= 0) return new ArrayList<>();

        List<AiChatMessage> messages = listLastMessagesByConversation(userId, conversationId, limit);

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

    /** 重命名会话标题 */
    @Transactional(rollbackFor = Exception.class)
    public void renameConversation(Long conversationId, String title) {
        var c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setTitle(title);
            c.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(c);
        }
    }

    /** 归档会话（软删除：设置 status=9） */
    @Transactional(rollbackFor = Exception.class)
    public void archiveConversation(Long conversationId) {
        var c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setStatus(9);
            c.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(c);
        }
    }

    /** 删除会话（通过外键约束自动删除消息） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long conversationId) {
        conversationMapper.deleteById(conversationId);
    }
}
