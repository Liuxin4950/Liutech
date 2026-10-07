package chat.liuxin.ai.service;

import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.entity.AiConversation;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.PromptBudget;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiConversationMapper;
import chat.liuxin.ai.mapper.AiUserStateMapper;
import chat.liuxin.ai.service.impl.AiChatServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 覆盖权限失败后的写入副作用，以及实际生成的分页 SQL。 */
class MemoryServiceTest {
    private AiChatMessageMapper messages;
    private AiConversationMapper conversations;
    private MemoryService memory;
    private AiConversation owned;

    @BeforeEach
    void setUp() {
        var builder = new MapperBuilderAssistant(new MybatisConfiguration(), "memory-test");
        TableInfoHelper.initTableInfo(builder, AiChatMessage.class);
        TableInfoHelper.initTableInfo(builder, AiConversation.class);
        messages = mock(AiChatMessageMapper.class);
        conversations = mock(AiConversationMapper.class);
        memory = new MemoryService(messages, conversations, mock(AiUserStateMapper.class));
        owned = new AiConversation();
        owned.setId(99L);
        owned.setUserId("42");
        owned.setMessageCount(5);
        when(conversations.selectById(99L)).thenReturn(owned);
        when(conversations.lockById(99L)).thenReturn(owned);
    }

    @Test
    void foreignUserMessageDoesNotReadOrWriteMessages() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> memory.saveUserMessage("7", 99L, "侵入消息", "model", null));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertNoMessageWrite();
    }

    @Test
    void foreignAssistantMessageCannotChangeConversation() {
        assertThrows(ResponseStatusException.class, () -> memory.saveAssistantMessage(
                "7", 99L, "侵入回复", "model", MemoryService.MESSAGE_STATUS_NORMAL, null));
        assertNoMessageWrite();
    }

    @Test
    void errorFallbackCannotWriteToForeignConversation() {
        var helper = new ChatServiceHelper(null, memory, null, null);
        helper.saveErrorIfNeeded(false, "7", 99L, "model");
        assertNoMessageWrite();
    }

    @Test
    void missingConversationDoesNotCreateOrphanMessage() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> memory.saveUserMessage("42", 123L, "消息", "model", null));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(messages);
        verify(conversations, never()).incrementMessageCount(anyLong(), any());
    }

    @Test
    void ownerCanSaveUserAndAssistantMessagesAndUpdateCount() {
        when(messages.selectOne(any(Wrapper.class))).thenReturn(message(5), message(6));
        when(messages.insert(any(AiChatMessage.class))).thenReturn(1);
        when(conversations.incrementMessageCount(anyLong(), any())).thenReturn(1);
        memory.saveUserMessage("42", 99L, "问题", "model", null);
        memory.saveAssistantMessage("42", 99L, "回答", "model", MemoryService.MESSAGE_STATUS_NORMAL, null);
        var captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messages, times(2)).insert(captor.capture());
        assertEquals(List.of("user", "assistant"), captor.getAllValues().stream().map(AiChatMessage::getRole).toList());
        assertTrue(captor.getAllValues().stream().allMatch(m -> "42".equals(m.getUserId()) && m.getConversationId() == 99L));
        assertEquals(List.of(6, 7), captor.getAllValues().stream().map(AiChatMessage::getSeqNo).toList());
        verify(conversations, times(2)).incrementMessageCount(eq(99L), any());
        // 落库统计只由原子 SQL 更新，不能把查出的旧实体写回。
        verify(conversations, never()).updateById(any(AiConversation.class));
        verify(conversations, never()).selectById(anyLong());
    }

    @Test
    void messageWriteLocksAndChecksOwnerBeforeReadingSequenceAndInserting() {
        when(messages.insert(any(AiChatMessage.class))).thenReturn(1);
        when(conversations.incrementMessageCount(anyLong(), any())).thenReturn(1);
        when(messages.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            assertTrue(((Wrapper<?>) invocation.getArgument(0)).getSqlSegment().endsWith("LIMIT 1 FOR UPDATE"));
            return message(5);
        });
        memory.saveUserMessage("42", 99L, "问题", "model", null);
        var order = inOrder(conversations, messages);
        order.verify(conversations).lockById(99L);
        order.verify(messages).selectOne(any(Wrapper.class));
        order.verify(messages).insert(any(AiChatMessage.class));
        order.verify(conversations).incrementMessageCount(eq(99L), any());
    }

    @Test
    void failedMessageInsertDoesNotIncrementConversationCount() {
        doThrow(new IllegalStateException("插入失败")).when(messages).insert(any(AiChatMessage.class));
        assertThrows(IllegalStateException.class, () -> memory.saveUserMessage("42", 99L, "问题", "model", null));
        verify(conversations, never()).incrementMessageCount(anyLong(), any());
    }

    @Test
    void renameAndArchiveOnlyUpdateTheirOwnFields() {
        memory.renameConversation("42", 99L, "新标题");
        memory.archiveConversation("42", 99L);
        var updates = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversations, times(2)).updateById(updates.capture());
        AiConversation rename = updates.getAllValues().get(0);
        assertEquals("新标题", rename.getTitle());
        assertNull(rename.getStatus());
        AiConversation archive = updates.getAllValues().get(1);
        assertEquals(MemoryService.CONVERSATION_STATUS_ARCHIVED, archive.getStatus());
        assertNull(archive.getTitle());
        for (AiConversation update : updates.getAllValues()) {
            assertEquals(99L, update.getId());
            assertNotNull(update.getUpdatedAt());
            assertNull(update.getMessageCount());
            assertNull(update.getLastMessageAt());
            assertNull(update.getUserId());
            assertNull(update.getCreatedAt());
        }
        verify(conversations, never()).selectById(anyLong());
    }

    @Test
    void conversationDeleteUsesSameLockOrderAsMessageWrites() {
        memory.deleteConversation("42", 99L);
        var order = inOrder(conversations, messages);
        order.verify(conversations).lockById(99L);
        order.verify(messages).delete(any(Wrapper.class));
        order.verify(conversations).deleteById(99L);
    }

    @Test
    void clearMemoryLocksConversationsInOrderBeforeDeletingTheirMessages() {
        when(conversations.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            String sql = ((Wrapper<?>) invocation.getArgument(0)).getSqlSegment();
            assertTrue(sql.endsWith("ORDER BY id ASC FOR UPDATE"));
            return List.of(owned);
        });
        when(messages.delete(any(Wrapper.class))).thenReturn(5);
        when(conversations.delete(any(Wrapper.class))).thenReturn(1);
        assertEquals(new MemoryService.PurgeCounts(1, 5), memory.clearAllMemory("42"));
        var order = inOrder(conversations, messages);
        order.verify(conversations).selectList(any(Wrapper.class));
        order.verify(messages).delete(any(Wrapper.class));
        order.verify(conversations).delete(any(Wrapper.class));
    }

    @Test
    void syncChatPreservesForbiddenStatusWithoutErrorWriteOrModelCall() {
        var props = new AiChatProperties();
        var budget = new PromptBudget(props);
        PromptService prompt = mock(PromptService.class);
        when(prompt.assembleParts(any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    memory.listLastMessagesByConversation("7", 99L, 8);
                    return null;
                });
        var helper = new ChatServiceHelper(prompt, memory, budget, null);
        AiModelPolicy policy = mock(AiModelPolicy.class);
        when(policy.resolveModelName(any())).thenReturn("model");
        when(policy.resolveParameters(any(), any())).thenReturn(
                new AiModelPolicy.ModelParameters(0.7, 1024, 8192, 6656, false, false, "test"));
        SiliconFlowChatClient client = mock(SiliconFlowChatClient.class);
        var service = new AiChatServiceImpl(props, client, memory, mock(AiMetrics.class), helper, policy, null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.processChat(request(99L), 7L, "USER"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(client);
        assertNoMessageWrite();
    }

    @Test
    void streamRejectsForeignConversationBeforeSchedulingOrCallingModel() {
        SiliconFlowChatClient client = mock(SiliconFlowChatClient.class);
        ChatServiceHelper helper = mock(ChatServiceHelper.class);
        // 无需初始化线程池：权限失败必须发生在创建 SSE 和调度任务之前。
        var service = new StreamingChatService(client, memory, helper, null, null, null, new AiChatProperties(), null, null, null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.processStreamChat(request(99L), 7L, "model", null, "USER"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(client, helper);
        assertNoMessageWrite();
    }

    @Test
    void conversationPagesProduceDistinctLimitedQueries() {
        List<String> sql = new ArrayList<>();
        when(conversations.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            sql.add(((Wrapper<?>) invocation.getArgument(0)).getSqlSegment());
            return new ArrayList<>();
        });
        memory.listConversations("42", null, 1, 20);
        memory.listConversations("42", null, 2, 20);
        assertTrue(sql.get(0).endsWith("LIMIT 0, 20"));
        assertTrue(sql.get(1).endsWith("LIMIT 20, 20"));
    }

    @Test
    void paginationClampsBeforeCalculatingOffsetAndAvoidsIntegerOverflow() {
        List<String> sql = new ArrayList<>();
        when(conversations.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            sql.add(((Wrapper<?>) invocation.getArgument(0)).getSqlSegment());
            return new ArrayList<>();
        });
        memory.listConversations("42", null, 2, 500);
        memory.listConversations("42", null, -1, -5);
        memory.listConversations("42", null, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertTrue(sql.get(0).endsWith("LIMIT 100, 100"));
        assertTrue(sql.get(1).endsWith("LIMIT 0, 1"));
        assertTrue(sql.get(2).endsWith("LIMIT 214748364600, 100"));
    }

    @Test
    void historyPaginationUsesLongOffsetAndPreservesDefaultPageSize() {
        memory.listHistoryMessages("42", Integer.MAX_VALUE, Integer.MAX_VALUE);
        verify(messages).selectHistoryMessagesByUserId("42", 214748364600L, 100);
        memory.listHistoryMessages("42", -1, -1);
        verify(messages).selectHistoryMessagesByUserId("42", 0L, 20);
    }

    @Test
    void messagePagesStartWithRecentMessagesAndReturnChronologicalOrder() {
        List<String> sql = new ArrayList<>();
        AiChatMessage older = message(2);
        AiChatMessage newer = message(3);
        when(messages.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            sql.add(((Wrapper<?>) invocation.getArgument(0)).getSqlSegment());
            return new ArrayList<>(List.of(newer, older));
        });
        assertEquals(List.of(older, newer), memory.listMessagesByConversation("42", 99L, 1, 2));
        memory.listMessagesByConversation("42", 99L, 2, 2);
        assertTrue(sql.get(0).contains("ORDER BY seq_no DESC,id DESC"));
        assertTrue(sql.get(0).endsWith("LIMIT 0, 2"));
        assertTrue(sql.get(1).endsWith("LIMIT 2, 2"));
    }

    @Test
    void promptHistoryIsLimitedInDatabaseAndRestoresChronologicalOrder() {
        AiChatMessage older = message(2);
        AiChatMessage newer = message(3);
        when(messages.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            String sql = ((Wrapper<?>) invocation.getArgument(0)).getSqlSegment();
            assertTrue(sql.endsWith("LIMIT 8"));
            return new ArrayList<>(List.of(newer, older));
        });
        assertEquals(List.of(older, newer), memory.listLastMessagesByConversation("42", 99L, 8));
    }

    private void assertNoMessageWrite() {
        verifyNoInteractions(messages);
        verify(conversations, never()).updateById(any(AiConversation.class));
        verify(conversations, never()).incrementMessageCount(anyLong(), any());
        assertEquals(5, owned.getMessageCount());
    }

    private ChatRequest request(Long id) {
        ChatRequest request = new ChatRequest();
        request.setMessage("问题");
        request.setConversationId(id);
        return request;
    }

    private AiChatMessage message(int seq) {
        AiChatMessage message = new AiChatMessage();
        message.setSeqNo(seq);
        return message;
    }
}
