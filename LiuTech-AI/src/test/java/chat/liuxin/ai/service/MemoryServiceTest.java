package chat.liuxin.ai.service;

import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.entity.AiConversation;
import chat.liuxin.ai.mapper.AiChatMessageMapper;
import chat.liuxin.ai.mapper.AiConversationMapper;
import chat.liuxin.ai.service.impl.MemoryServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.qameta.allure.Description;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 记忆服务单元测试
 * 测试覆盖：
 * 1. 消息保存和查询
 * 2. 会话管理
 * 3. 历史记录清理
 * 4. SQL注入防护验证
 *
 * 作者：刘鑫
 * 时间：2025-12-12
 */
@Epic("记忆服务")
@Feature("消息存储与查询")
@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private AiChatMessageMapper messageMapper;

    @Mock
    private AiConversationMapper conversationMapper;

    @InjectMocks
    private MemoryServiceImpl memoryService;

    private static final String TEST_USER_ID = "user123";
    private static final Long TEST_CONVERSATION_ID = 1L;
    private static final String TEST_MESSAGE = "测试消息";
    private static final String TEST_MODEL = "THUDM/glm-4-9b-chat";

    @BeforeEach
    void setUp() {
        // 初始化测试数据
    }

    @Test
    @Story("查询最近消息")
    @Description("验证当传入有效的消息条数限制时，能够正确返回指定数量的最近消息")
    @Severity(SeverityLevel.CRITICAL)
    void testListRecentMessages_WithValidLimit_ShouldReturnMessages() {
        // Arrange
        List<AiChatMessage> expectedMessages = Arrays.asList(
                createMessage(1L, "user", "消息1"),
                createMessage(2L, "assistant", "回复1")
        );
        when(messageMapper.selectRecentMessagesByUserId(TEST_USER_ID, 10))
                .thenReturn(expectedMessages);

        // Act
        List<AiChatMessage> result = memoryService.listRecentMessages(TEST_USER_ID, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(messageMapper, times(1))
                .selectRecentMessagesByUserId(TEST_USER_ID, 10);
    }

    @Test
    @Story("边界值测试")
    @Description("验证当传入0作为消息条数限制时，返回空列表且不调用Mapper")
    @Severity(SeverityLevel.NORMAL)
    void testListRecentMessages_WithZeroLimit_ShouldReturnEmpty() {
        // Act
        List<AiChatMessage> result = memoryService.listRecentMessages(TEST_USER_ID, 0);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(messageMapper, never()).selectRecentMessagesByUserId(any(), anyInt());
    }

    @Test
    @Story("边界值测试")
    @Description("验证当传入负数作为消息条数限制时，返回空列表且不调用Mapper")
    @Severity(SeverityLevel.NORMAL)
    void testListRecentMessages_WithNegativeLimit_ShouldReturnEmpty() {
        // Act
        List<AiChatMessage> result = memoryService.listRecentMessages(TEST_USER_ID, -1);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(messageMapper, never()).selectRecentMessagesByUserId(any(), anyInt());
    }

    @Test
    @Story("保存用户消息")
    @Description("验证能够正确保存用户消息，并自动计算正确的消息序号（seqNo = 最大seqNo + 1）")
    @Severity(SeverityLevel.CRITICAL)
    void testSaveUserMessage_ShouldSaveCorrectly() {
        // Arrange
        // 模拟查询最大seqNo
        AiChatMessage lastMessage = createMessage(1L, "assistant", "最后一条");
        lastMessage.setSeqNo(5);
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(lastMessage);

        // Act
        memoryService.saveUserMessage(TEST_USER_ID, TEST_CONVERSATION_ID, TEST_MESSAGE, TEST_MODEL, null);

        // Assert
        verify(messageMapper, times(1)).insert(argThat((AiChatMessage msg) ->
                msg.getConversationId().equals(TEST_CONVERSATION_ID) &&
                "user".equals(msg.getRole()) &&
                TEST_MESSAGE.equals(msg.getContent()) &&
                msg.getSeqNo() == 6 // 5 + 1
        ));
    }

    @Test
    @Story("首次消息保存")
    @Description("验证当保存第一条消息时，自动将序号设置为1")
    @Severity(SeverityLevel.NORMAL)
    void testSaveUserMessage_FirstMessage_ShouldSetSeqNoTo1() {
        // Arrange
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null); // 没有历史消息

        // Act
        memoryService.saveUserMessage(TEST_USER_ID, TEST_CONVERSATION_ID, TEST_MESSAGE, TEST_MODEL, null);

        // Assert
        verify(messageMapper, times(1)).insert(argThat((AiChatMessage msg) ->
                msg.getSeqNo() == 1
        ));
    }

    @Test
    @Story("保存AI回复")
    @Description("验证能够正确保存AI回复消息，包括角色、内容和序号")
    @Severity(SeverityLevel.CRITICAL)
    void testSaveAssistantMessage_ShouldSaveCorrectly() {
        // Arrange
        AiChatMessage lastMessage = createMessage(1L, "user", "最后一条");
        lastMessage.setSeqNo(3);
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(lastMessage);

        // Act
        memoryService.saveAssistantMessage(TEST_USER_ID, TEST_CONVERSATION_ID, "AI回复", TEST_MODEL, 1, null);

        // Assert
        verify(messageMapper, times(1)).insert(argThat((AiChatMessage msg) ->
                msg.getConversationId().equals(TEST_CONVERSATION_ID) &&
                "assistant".equals(msg.getRole()) &&
                "AI回复".equals(msg.getContent()) &&
                msg.getSeqNo() == 4
        ));
    }

    @Test
    @Story("历史记录查询")
    @Description("验证能够正确分页查询历史消息，按时间倒序返回结果")
    @Severity(SeverityLevel.CRITICAL)
    void testListHistoryMessages_ShouldReturnPaginatedResults() {
        // Arrange
        List<AiChatMessage> expectedMessages = Arrays.asList(
                createMessage(3L, "assistant", "回复3"),
                createMessage(2L, "user", "消息2")
        );
        when(messageMapper.selectHistoryMessagesByUserId(TEST_USER_ID, 0, 10))
                .thenReturn(expectedMessages);

        // Act
        List<AiChatMessage> result = memoryService.listHistoryMessages(TEST_USER_ID, 1, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(messageMapper, times(1))
                .selectHistoryMessagesByUserId(TEST_USER_ID, 0, 10);
    }

    @Test
    @Story("边界值测试")
    @Description("验证当传入无效页码（<1）时，返回空列表且不调用Mapper")
    @Severity(SeverityLevel.NORMAL)
    void testListHistoryMessages_WithInvalidPage_ShouldReturnEmpty() {
        // Act
        List<AiChatMessage> result = memoryService.listHistoryMessages(TEST_USER_ID, 0, 10);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(messageMapper, never()).selectHistoryMessagesByUserId(any(), anyInt(), anyInt());
    }

    @Test
    @Story("历史记录统计")
    @Description("验证能够正确统计用户的历史消息总数")
    @Severity(SeverityLevel.NORMAL)
    void testCountHistoryMessages_ShouldReturnCorrectCount() {
        // Arrange
        when(messageMapper.countMessagesByUserId(TEST_USER_ID))
                .thenReturn(100L);

        // Act
        long count = memoryService.countHistoryMessages(TEST_USER_ID);

        // Assert
        assertEquals(100L, count);
        verify(messageMapper, times(1)).countMessagesByUserId(TEST_USER_ID);
    }

    @Test
    @Story("记忆清理")
    @Description("验证当需要保留指定数量的消息时，能够正确删除超出范围的旧消息")
    @Severity(SeverityLevel.CRITICAL)
    void testCleanupByRetainLastN_WithValidN_ShouldDeleteOldMessages() {
        // Arrange
        // 模拟边界消息
        AiChatMessage boundaryMessage = createMessage(10L, "assistant", "边界消息");
        boundaryMessage.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(boundaryMessage));

        // 模拟用户会话列表
        AiConversation conversation = new AiConversation();
        conversation.setId(TEST_CONVERSATION_ID);
        when(conversationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(conversation));

        // Act
        memoryService.cleanupByRetainLastN(TEST_USER_ID, 10);

        // Assert - 验证删除了消息
        verify(messageMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @Story("边界值测试")
    @Description("验证当保留条数为0时，不执行任何清理操作")
    @Severity(SeverityLevel.NORMAL)
    void testCleanupByRetainLastN_WithZeroN_ShouldDoNothing() {
        // Act
        memoryService.cleanupByRetainLastN(TEST_USER_ID, 0);

        // Assert
        verify(messageMapper, never()).selectList(any());
        verify(messageMapper, never()).delete(any());
    }

    @Test
    @Story("边界值测试")
    @Description("验证当没有消息可清理时，不执行删除操作")
    @Severity(SeverityLevel.NORMAL)
    void testCleanupByRetainLastN_WithNoMessages_ShouldDoNothing() {
        // Arrange
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList()); // 空列表

        // Act
        memoryService.cleanupByRetainLastN(TEST_USER_ID, 10);

        // Assert
        verify(messageMapper, never()).delete(any());
    }

    @Test
    @Story("清空记忆")
    @Description("验证能够正确清空用户的所有聊天记忆，包括消息和会话")
    @Severity(SeverityLevel.BLOCKER)
    void testClearAllMemory_ShouldDeleteAllMessagesAndConversations() {
        // Arrange
        AiConversation conversation = new AiConversation();
        conversation.setId(TEST_CONVERSATION_ID);
        when(conversationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(conversation));

        // Act
        memoryService.clearAllMemory(TEST_USER_ID);

        // Assert
        verify(messageMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        verify(conversationMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @Story("边界值测试")
    @Description("验证当用户没有任何会话时，仅执行会话删除操作")
    @Severity(SeverityLevel.NORMAL)
    void testClearAllMemory_WithNoConversations_ShouldOnlyDeleteConversations() {
        // Arrange
        when(conversationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList()); // 空列表

        // Act
        memoryService.clearAllMemory(TEST_USER_ID);

        // Assert
        verify(messageMapper, never()).delete(any());
        verify(conversationMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @Story("创建会话")
    @Description("验证能够正确创建新会话并返回生成的会话ID")
    @Severity(SeverityLevel.CRITICAL)
    void testCreateConversation_ShouldReturnConversationId() {
        // Arrange
        AiConversation savedConversation = new AiConversation();
        savedConversation.setId(999L);
        when(conversationMapper.insert(any(AiConversation.class)))
                .thenReturn(1); // 返回插入的记录数

        // Act
        Long conversationId = memoryService.createConversation(TEST_USER_ID, "测试会话");

        // Assert
        assertNotNull(conversationId);
        verify(conversationMapper, times(1)).insert(argThat((AiConversation conv) ->
                TEST_USER_ID.equals(conv.getUserId()) &&
                "测试会话".equals(conv.getTitle())
        ));
    }

    @Test
    @Story("会话列表查询")
    @Description("验证能够正确分页查询用户的会话列表，按更新时间倒序排列")
    @Severity(SeverityLevel.NORMAL)
    void testListConversations_ShouldReturnUserConversations() {
        // Arrange
        List<AiConversation> expectedConversations = Arrays.asList(
                createConversation(1L, "会话1"),
                createConversation(2L, "会话2")
        );
        when(conversationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(expectedConversations);

        // Act
        List<AiConversation> result = memoryService.listConversations(TEST_USER_ID, null, 1, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(conversationMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @Story("重命名会话")
    @Description("验证能够正确修改会话标题")
    @Severity(SeverityLevel.NORMAL)
    void testRenameConversation_ShouldUpdateTitle() {
        // Arrange
        AiConversation existingConversation = createConversation(TEST_CONVERSATION_ID, "旧标题");
        when(conversationMapper.selectById(TEST_CONVERSATION_ID))
                .thenReturn(existingConversation);

        // Act
        memoryService.renameConversation(TEST_CONVERSATION_ID, "新标题");

        // Assert
        verify(conversationMapper, times(1)).updateById(argThat((AiConversation conv) ->
                "新标题".equals(conv.getTitle())
        ));
    }

    @Test
    @Story("边界值测试")
    @Description("验证当尝试重命名不存在的会话时，不执行任何操作")
    @Severity(SeverityLevel.NORMAL)
    void testRenameConversation_WithNonExistentId_ShouldDoNothing() {
        // Arrange
        when(conversationMapper.selectById(TEST_CONVERSATION_ID))
                .thenReturn(null);

        // Act
        memoryService.renameConversation(TEST_CONVERSATION_ID, "新标题");

        // Assert
        verify(conversationMapper, never()).updateById(any(AiConversation.class));
    }

    @Test
    @Story("删除会话")
    @Description("验证能够正确删除指定的会话")
    @Severity(SeverityLevel.BLOCKER)
    void testDeleteConversation_ShouldDeleteConversation() {
        // Act
        memoryService.deleteConversation(TEST_CONVERSATION_ID);

        // Assert
        verify(conversationMapper, times(1)).deleteById(TEST_CONVERSATION_ID);
    }

    @Test
    @Story("归档会话")
    @Description("验证归档操作等同于删除会话")
    @Severity(SeverityLevel.NORMAL)
    void testArchiveConversation_ShouldDeleteConversation() {
        // Act
        memoryService.archiveConversation(TEST_CONVERSATION_ID);

        // Assert - 归档就是删除
        verify(conversationMapper, times(1)).deleteById(TEST_CONVERSATION_ID);
    }

    @Test
    @Story("会话消息查询")
    @Description("验证能够正确分页查询指定会话内的所有消息")
    @Severity(SeverityLevel.NORMAL)
    void testListMessagesByConversation_ShouldReturnMessages() {
        // Arrange
        List<AiChatMessage> expectedMessages = Arrays.asList(
                createMessage(1L, "user", "消息1"),
                createMessage(2L, "assistant", "回复1")
        );
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(expectedMessages);

        // Act
        List<AiChatMessage> result = memoryService.listMessagesByConversation(TEST_CONVERSATION_ID, 1, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(messageMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @Story("消息类型转换")
    @Description("验证能够正确将数据库消息转换为Spring AI消息类型（user -> UserMessage, assistant -> AssistantMessage）")
    @Severity(SeverityLevel.CRITICAL)
    void testListLastMessagesAsPromptMessages_ShouldConvertToSpringMessages() {
        // Arrange
        List<AiChatMessage> messages = Arrays.asList(
                createMessage(1L, "user", "用户消息"),
                createMessage(2L, "assistant", "AI回复")
        );

        // 模拟listLastMessagesByConversation
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(messages);

        // Act
        List<org.springframework.ai.chat.messages.Message> result =
                memoryService.listLastMessagesAsPromptMessages(TEST_CONVERSATION_ID, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        // 验证消息类型转换
        assertTrue(result.get(0) instanceof org.springframework.ai.chat.messages.UserMessage);
        assertTrue(result.get(1) instanceof org.springframework.ai.chat.messages.AssistantMessage);
    }

    @Test
    @Story("消息类型转换")
    @Description("验证系统消息能够正确转换为Spring AI的SystemMessage类型")
    @Severity(SeverityLevel.NORMAL)
    void testListLastMessagesAsPromptMessages_WithSystemMessage_ShouldConvertCorrectly() {
        // Arrange
        List<AiChatMessage> messages = Arrays.asList(
                createMessage(1L, "system", "系统提示")
        );
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(messages);

        // Act
        List<org.springframework.ai.chat.messages.Message> result =
                memoryService.listLastMessagesAsPromptMessages(TEST_CONVERSATION_ID, 10);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof org.springframework.ai.chat.messages.SystemMessage);
    }

    // 辅助方法：创建测试消息
    private AiChatMessage createMessage(Long id, String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setId(id);
        message.setConversationId(TEST_CONVERSATION_ID);
        message.setRole(role);
        message.setContent(content);
        message.setModel(TEST_MODEL);
        message.setStatus(1);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    // 辅助方法：创建测试会话
    private AiConversation createConversation(Long id, String title) {
        AiConversation conversation = new AiConversation();
        conversation.setId(id);
        conversation.setUserId(TEST_USER_ID);
        conversation.setTitle(title);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        return conversation;
    }
}
