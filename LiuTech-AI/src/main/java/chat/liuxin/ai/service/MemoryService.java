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
 * 管理聊天会话与消息的持久化，包括会话 CRUD、消息保存与查询、历史清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final AiChatMessageMapper messageMapper;
    private final AiConversationMapper conversationMapper;

    /**
     * 查用户最近 N 条消息用于跨会话记忆拼接,数据库层按倒序取后由 mapper 返回升序,可直接喂给 prompt。
     */
    public List<AiChatMessage> listRecentMessages(String userId, int limit) {
        if (limit <= 0) return Collections.emptyList();
        return messageMapper.selectRecentMessagesByUserId(userId, limit);
    }

    /**
     * 分页查用户所有历史消息(倒序),供管理后台/个人中心的消息记录列表使用。
     */
    public List<AiChatMessage> listHistoryMessages(String userId, int page, int size) {
        if (page < 1 || size <= 0) return Collections.emptyList();
        int offset = (page - 1) * size;
        return messageMapper.selectHistoryMessagesByUserId(userId, offset, size);
    }

    /** 与 {@link #listHistoryMessages} 配对的总数查询,用于分页控件。 */
    public long countHistoryMessages(String userId) {
        return messageMapper.countMessagesByUserId(userId);
    }

    /**
     * 落库一条用户消息。
     *
     * seqNo 取自会话内当前最大值 +1(保序);同时触发 {@link #touchConversation} 刷新会话统计字段。
     */
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
        touchConversation(conversationId);
    }

    /**
     * 落库一条 AI 回复。
     *
     * status:1=正常完成,3=异常(流式中断时会传 partial 文本或 null 占位)。
     * 同样按 seqNo 保序并刷新会话统计。
     */
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
        touchConversation(conversationId);
    }

    /**
     * 每插入一条消息后调,累加会话 messageCount、刷新 lastMessageAt / updatedAt。
     * 会话不存在(如访客虚拟会话或已被删)时静默跳过。
     */
    private void touchConversation(Long conversationId) {
        if (conversationId == null) return;
        AiConversation c = conversationMapper.selectById(conversationId);
        if (c == null) return;
        c.setMessageCount((c.getMessageCount() == null ? 0 : c.getMessageCount()) + 1);
        c.setLastMessageAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(c);
    }

    /**
     * 取会话内当前最大 seqNo,新消息插入时用于生成下一个序号。空会话返回 0。
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

    /**
     * 物理删除用户名下所有会话及其消息(不可恢复)。用户主动"清空记忆"入口。
     */
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

    /**
     * 建新会话并返回主键 id。看板娘/写作助手在首条消息前没有 conversationId 时会调这里补建。
     */
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

    /**
     * 会话列表分页查询,排除已归档(status=9),按更新时间倒序,size 上限 100 防滥用。
     */
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

    /** 按主键查会话,不做权限校验,内部/管理场景使用。 */
    public AiConversation getConversation(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    /**
     * 查会话并校验属主。
     *
     * 会话不存在抛 404,归属他人或匿名访问抛 403。所有面向前端的会话读写都应经过这里,防越权。
     */
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

    /**
     * 分页取指定会话内的消息,按 seqNo 升序还原对话时序。会先做属主校验。
     */
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

    /**
     * 取会话末尾 N 条(数据库倒序取后反转成升序),用于恢复对话状态或提示词上下文。
     */
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

    /**
     * 拉最近 N 条并转成 Spring AI 的 {@link Message} 类型(按 role 映射为 System/Assistant/User),
     * 供 {@link PromptService} 拼装提示词直接使用。
     */
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

    /** 重命名会话标题;会话不存在时静默跳过。调用方应先做属主校验。 */
    @Transactional(rollbackFor = Exception.class)
    public void renameConversation(Long conversationId, String title) {
        var c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setTitle(title);
            c.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(c);
        }
    }

    /** 软删除会话:置 status=9,列表查询会自动过滤,消息数据保留。 */
    @Transactional(rollbackFor = Exception.class)
    public void archiveConversation(Long conversationId) {
        var c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setStatus(9);
            c.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(c);
        }
    }

    /**
     * 物理删除会话及其全部消息,先删消息再删会话避免孤儿数据。
     * 调用方需先做属主校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long conversationId) {
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }
}
