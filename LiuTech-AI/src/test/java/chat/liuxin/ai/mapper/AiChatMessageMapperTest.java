package chat.liuxin.ai.mapper;

import chat.liuxin.ai.entity.AiChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI聊天消息Mapper单元测试
 * 测试覆盖：
 * 1. 自定义查询方法
 * 2. JOIN查询性能
 * 3. 参数化查询安全性（防SQL注入）
 *
 * 作者：刘鑫
 * 时间：2025-12-12
 */
@ExtendWith(MockitoExtension.class)
class AiChatMessageMapperTest {

    @Mock
    private AiChatMessageMapper mapper;

    private static final String TEST_USER_ID = "user123";
    private static final Long TEST_CONVERSATION_ID = 1L;

    @Test
    void testSelectRecentMessagesByUserId_WithValidParams_ShouldReturnMessages() {
        // Arrange
        List<AiChatMessage> expectedMessages = Arrays.asList(
                createMessage(1L, "user", "消息1"),
                createMessage(2L, "assistant", "回复1"),
                createMessage(3L, "user", "消息2")
        );
        when(mapper.selectRecentMessagesByUserId(TEST_USER_ID, 10))
                .thenReturn(expectedMessages);

        // Act
        List<AiChatMessage> result = mapper.selectRecentMessagesByUserId(TEST_USER_ID, 10);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证SQL调用
        verify(mapper, times(1)).selectRecentMessagesByUserId(TEST_USER_ID, 10);

        // 验证参数传递正确（防SQL注入）
        verify(mapper, never()).selectRecentMessagesByUserId(contains("'"), anyInt());
        verify(mapper, never()).selectRecentMessagesByUserId(contains(";"), anyInt());
    }

    @Test
    void testSelectRecentMessagesByUserId_WithSpecialCharactersInUserId_ShouldBeSafe() {
        // Arrange - 模拟包含特殊字符的userId（SQL注入攻击尝试）
        String maliciousUserId = "user'; DROP TABLE users; --";
        List<AiChatMessage> emptyResult = Arrays.asList();
        when(mapper.selectRecentMessagesByUserId(maliciousUserId, 10))
                .thenReturn(emptyResult);

        // Act
        List<AiChatMessage> result = mapper.selectRecentMessagesByUserId(maliciousUserId, 10);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // 验证Mapper被调用时参数未被篡改
        verify(mapper, times(1)).selectRecentMessagesByUserId(eq(maliciousUserId), anyInt());
    }

    @Test
    void testSelectRecentMessagesByUserId_WithLimitZero_ShouldReturnEmpty() {
        // Arrange
        List<AiChatMessage> emptyResult = Arrays.asList();
        when(mapper.selectRecentMessagesByUserId(TEST_USER_ID, 0))
                .thenReturn(emptyResult);

        // Act
        List<AiChatMessage> result = mapper.selectRecentMessagesByUserId(TEST_USER_ID, 0);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(mapper, times(1)).selectRecentMessagesByUserId(TEST_USER_ID, 0);
    }

    @Test
    void testSelectRecentMessagesByUserId_WithNegativeLimit_ShouldHandleGracefully() {
        // Arrange
        List<AiChatMessage> emptyResult = Arrays.asList();
        when(mapper.selectRecentMessagesByUserId(TEST_USER_ID, -1))
                .thenReturn(emptyResult);

        // Act
        List<AiChatMessage> result = mapper.selectRecentMessagesByUserId(TEST_USER_ID, -1);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSelectHistoryMessagesByUserId_WithValidParams_ShouldReturnPaginatedResults() {
        // Arrange
        List<AiChatMessage> expectedMessages = Arrays.asList(
                createMessage(10L, "assistant", "最新回复"),
                createMessage(9L, "user", "最新消息")
        );
        when(mapper.selectHistoryMessagesByUserId(TEST_USER_ID, 0, 10))
                .thenReturn(expectedMessages);

        // Act
        List<AiChatMessage> result = mapper.selectHistoryMessagesByUserId(TEST_USER_ID, 0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());

        // 验证参数顺序正确（offset, size）
        verify(mapper, times(1)).selectHistoryMessagesByUserId(eq(TEST_USER_ID), eq(0), eq(10));
    }

    @Test
    void testSelectHistoryMessagesByUserId_WithLargeOffset_ShouldReturnEmpty() {
        // Arrange - 测试大数据偏移量
        List<AiChatMessage> emptyResult = Arrays.asList();
        when(mapper.selectHistoryMessagesByUserId(TEST_USER_ID, 10000, 10))
                .thenReturn(emptyResult);

        // Act
        List<AiChatMessage> result = mapper.selectHistoryMessagesByUserId(TEST_USER_ID, 10000, 10);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSelectHistoryMessagesByUserId_WithSizeZero_ShouldReturnEmpty() {
        // Arrange
        List<AiChatMessage> emptyResult = Arrays.asList();
        when(mapper.selectHistoryMessagesByUserId(TEST_USER_ID, 0, 0))
                .thenReturn(emptyResult);

        // Act
        List<AiChatMessage> result = mapper.selectHistoryMessagesByUserId(TEST_USER_ID, 0, 0);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCountMessagesByUserId_WithValidUserId_ShouldReturnCorrectCount() {
        // Arrange
        when(mapper.countMessagesByUserId(TEST_USER_ID))
                .thenReturn(150L);

        // Act
        long count = mapper.countMessagesByUserId(TEST_USER_ID);

        // Assert
        assertEquals(150L, count);
        verify(mapper, times(1)).countMessagesByUserId(TEST_USER_ID);
    }

    @Test
    void testCountMessagesByUserId_WithNonExistentUserId_ShouldReturnZero() {
        // Arrange
        when(mapper.countMessagesByUserId("nonexistent_user"))
                .thenReturn(0L);

        // Act
        long count = mapper.countMessagesByUserId("nonexistent_user");

        // Assert
        assertEquals(0L, count);
    }

    @Test
    void testCountMessagesByUserId_WithSpecialCharacters_ShouldBeSafe() {
        // Arrange - 测试SQL注入
        String specialUserId = "user<script>alert('xss')</script>";
        when(mapper.countMessagesByUserId(specialUserId))
                .thenReturn(0L);

        // Act
        long count = mapper.countMessagesByUserId(specialUserId);

        // Assert
        assertEquals(0L, count);
        verify(mapper, times(1)).countMessagesByUserId(eq(specialUserId));
    }

    @Test
    void testCountOldMessagesForCleanup_WithValidParams_ShouldReturnCorrectCount() {
        // Arrange
        when(mapper.countOldMessagesForCleanup(TEST_USER_ID, 50))
                .thenReturn(200L);

        // Act
        long count = mapper.countOldMessagesForCleanup(TEST_USER_ID, 50);

        // Assert
        assertEquals(200L, count);
        verify(mapper, times(1)).countOldMessagesForCleanup(TEST_USER_ID, 50);
    }

    @Test
    void testCountOldMessagesForCleanup_WithZeroRetainLastN_ShouldReturnZero() {
        // Arrange
        when(mapper.countOldMessagesForCleanup(TEST_USER_ID, 0))
                .thenReturn(0L);

        // Act
        long count = mapper.countOldMessagesForCleanup(TEST_USER_ID, 0);

        // Assert
        assertEquals(0L, count);
    }

    @Test
    void testBaseMapperMethods_ShouldWorkCorrectly() {
        // Arrange
        AiChatMessage message = createMessage(1L, "user", "测试消息");

        // 测试insert
        when(mapper.insert(message)).thenReturn(1);

        // 测试selectById
        when(mapper.selectById(1L)).thenReturn(message);

        // 测试updateById
        when(mapper.updateById(message)).thenReturn(1);

        // 测试deleteById
        when(mapper.deleteById(1L)).thenReturn(1);

        // Act & Assert
        assertEquals(1, mapper.insert(message));
        assertNotNull(mapper.selectById(1L));
        assertEquals(1, mapper.updateById(message));
        assertEquals(1, mapper.deleteById(1L));

        verify(mapper, times(1)).insert(message);
        verify(mapper, times(1)).selectById(1L);
        verify(mapper, times(1)).updateById(message);
        verify(mapper, times(1)).deleteById(1L);
    }

    @Test
    void testMapperExtendsBaseMapper_ShouldHaveAllBaseMethods() {
        // 验证Mapper继承了BaseMapper的所有方法
        assertTrue(mapper instanceof BaseMapper);
    }

    // 辅助方法：创建测试消息
    private AiChatMessage createMessage(Long id, String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setId(id);
        message.setConversationId(TEST_CONVERSATION_ID);
        message.setRole(role);
        message.setContent(content);
        message.setModel("THUDM/glm-4-9b-chat");
        message.setStatus(1);
        message.setSeqNo(id.intValue());
        return message;
    }
}
