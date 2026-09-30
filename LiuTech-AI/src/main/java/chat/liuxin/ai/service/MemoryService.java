package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.entity.AiConversation;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    private final chat.liuxin.ai.mapper.AiUserStateMapper userStateMapper;

    // ========== 消息与会话状态常量 ==========
    /** 消息状态：正常完成 */
    public static final int MESSAGE_STATUS_NORMAL = 1;
    /** 消息状态：异常（流式中断） */
    public static final int MESSAGE_STATUS_ERROR = 3;
    /** 会话状态：正常 */
    public static final int CONVERSATION_STATUS_ACTIVE = 0;
    /** 会话状态：已归档（软删除） */
    public static final int CONVERSATION_STATUS_ARCHIVED = 9;

    /**
     * 查用户最近 N 条消息用于跨会话记忆拼接,数据库层按倒序取后由 mapper 返回升序,可直接喂给 prompt。
     */
    public List<AiChatMessage> listRecentMessages(String userId, int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<AiChatMessage> messages = messageMapper.selectRecentMessagesByUserId(userId, Math.min(limit, 100));
        Collections.reverse(messages);
        return messages;
    }

    /**
     * 分页查用户所有历史消息(倒序),供管理后台/个人中心的消息记录列表使用。
     */
    public List<AiChatMessage> listHistoryMessages(String userId, int page, int size) {
        if (page < 1 || size <= 0) return Collections.emptyList();
        int safeSize = safeSize(size);
        return messageMapper.selectHistoryMessagesByUserId(userId, offset(page, safeSize), safeSize);
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
        lockActiveUser(userId);
        lockOwnedConversation(userId, conversationId);
        Integer maxSeqNo = messageMapper.selectLastSeqNoForUpdate(conversationId);
        int nextSeqNo = Math.addExact(maxSeqNo == null ? 0 : maxSeqNo, 1);

        AiChatMessage m = new AiChatMessage();
        m.setUserId(userId);
        m.setConversationId(conversationId);
        m.setRole("user");
        m.setContent(content);
        m.setModel(model);
        m.setStatus(MESSAGE_STATUS_NORMAL);
        m.setSeqNo(nextSeqNo);
        m.setCreatedAt(LocalDateTime.now());
        if (messageMapper.insert(m) != 1) {
            throw new IllegalStateException("消息保存失败");
        }
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
        lockActiveUser(userId);
        lockOwnedConversation(userId, conversationId);
        Integer maxSeqNo = messageMapper.selectLastSeqNoForUpdate(conversationId);
        int nextSeqNo = Math.addExact(maxSeqNo == null ? 0 : maxSeqNo, 1);

        AiChatMessage m = new AiChatMessage();
        m.setUserId(userId);
        m.setConversationId(conversationId);
        m.setRole("assistant");
        m.setContent(content);
        m.setModel(model);
        m.setStatus(status);
        m.setSeqNo(nextSeqNo);
        m.setCreatedAt(LocalDateTime.now());
        if (messageMapper.insert(m) != 1) {
            throw new IllegalStateException("消息保存失败");
        }
        touchConversation(conversationId);
    }

    /**
     * 每插入一条消息后调,累加会话 messageCount、刷新 lastMessageAt / updatedAt。
     * 已由写入口锁定并校验会话，更新失败回滚整次消息保存。
     */
    private void touchConversation(Long conversationId) {
        if (conversationMapper.incrementMessageCount(conversationId) != 1) {
            throw new IllegalStateException("会话统计更新失败");
        }
    }

    private AiConversation lockOwnedConversation(String userId, Long conversationId) {
        return requireOwned(userId, conversationMapper.selectForUpdate(conversationId));
    }

    private AiConversation requireOwned(String userId, AiConversation conversation) {
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在或已删除");
        }
        if (userId == null || !userId.equals(conversation.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该会话");
        }
        return conversation;
    }

    private static int safeSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private static long offset(int page, int size) {
        return ((long) Math.max(1, page) - 1) * size;
    }

    /**
     * 物理删除用户名下所有会话及其消息(不可恢复)。用户主动"清空记忆"入口。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurgeCounts clearAllMemory(String userId) {
        List<Long> conversationIds = conversationMapper.selectOwnedForUpdate(userId)
                .stream().map(AiConversation::getId).toList();

        int deleted = 0;
        if (!conversationIds.isEmpty()) {
            deleted = messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                    .in(AiChatMessage::getConversationId, conversationIds)
            );
        }

        int deletedConversations = conversationMapper.delete(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
        );

        log.info("清空用户 AI 数据：userId={}, conversations={}, messages={}",
                userId, deletedConversations, deleted);
        return new PurgeCounts(deletedConversations, deleted);
    }

    public record PurgeCounts(int conversationsDeleted, int messagesDeleted) {}

    /** 内部永久清理；与用户主动清空记忆不同，后续旧请求不得重新创建数据。 */
    @Transactional(rollbackFor = Exception.class)
    public PurgeCounts purgeUserData(String userId) {
        userStateMapper.ensureExists(userId);
        userStateMapper.selectPurgedForUpdate(userId);
        userStateMapper.markPurged(userId);
        return clearAllMemory(userId);
    }

    private void lockActiveUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        userStateMapper.ensureExists(userId);
        if (Integer.valueOf(1).equals(userStateMapper.selectPurgedForUpdate(userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户数据已永久清理");
        }
    }

    /**
     * 建新会话并返回主键 id。看板娘/写作助手在首条消息前没有 conversationId 时会调这里补建。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createConversation(String userId, String title) {
        lockActiveUser(userId);
        AiConversation c = new AiConversation();
        LocalDateTime time = LocalDateTime.now();
        c.setUserId(userId);
        c.setTitle(title);
        c.setStatus(CONVERSATION_STATUS_ACTIVE);
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
        int safeSize = safeSize(size);
        return conversationMapper.selectVisiblePage(userId, offset(page, safeSize), safeSize);
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
        return requireOwned(userId, conversationMapper.selectById(conversationId));
    }

    /**
     * 分页取指定会话内的消息,按 seqNo 升序还原对话时序。会先做属主校验。
     */
    public List<AiChatMessage> listMessagesByConversation(String userId, Long conversationId, int page, int size) {
        getConversationOwnedByUser(userId, conversationId);
        int safeSize = safeSize(size);
        return messageMapper.selectConversationPage(conversationId, offset(page, safeSize), safeSize);
    }

    /**
     * 取会话末尾 N 条(数据库倒序取后反转成升序),用于恢复对话状态或提示词上下文。
     */
    public List<AiChatMessage> listLastMessagesByConversation(String userId, Long conversationId, int limit) {
        getConversationOwnedByUser(userId, conversationId);
        if (limit <= 0) return Collections.emptyList();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<AiChatMessage> messages = messageMapper.selectConversationTail(conversationId, safeLimit);
        Collections.reverse(messages);
        return messages;
    }

    /**
     * 拉最近 N 条并转成 Spring AI 的 {@link Message} 类型(按 role 映射为 System/Assistant/User),
     * 供 {@link PromptService} 拼装提示词直接使用。
     */
    public List<Message> listLastMessagesAsPromptMessages(String userId, Long conversationId, int limit) {
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

    /** 属主校验与字段更新在同一事务中完成，避免覆盖消息计数。 */
    @Transactional(rollbackFor = Exception.class)
    public void renameConversation(String userId, Long conversationId, String title) {
        lockOwnedConversation(userId, conversationId);
        conversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .set(AiConversation::getTitle, title)
                .set(AiConversation::getUpdatedAt, LocalDateTime.now()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void archiveConversation(String userId, Long conversationId) {
        lockOwnedConversation(userId, conversationId);
        conversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .set(AiConversation::getStatus, CONVERSATION_STATUS_ARCHIVED)
                .set(AiConversation::getUpdatedAt, LocalDateTime.now()));
    }

    /** 与消息保存一致：先锁会话，再操作消息。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(String userId, Long conversationId) {
        lockOwnedConversation(userId, conversationId);
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }
}
