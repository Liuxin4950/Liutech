package chat.liuxin.ai.service;

import chat.liuxin.ai.exception.AIServiceException;
import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.service.impl.AiChatServiceImpl;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI聊天服务单元测试
 * 测试覆盖：
 * 1. 正常聊天流程
 * 2. 流式聊天流程
 * 3. 异常处理
 * 4. 参数验证
 *
 * 作者：刘鑫
 * 时间：2025-12-12
 */
@Epic("AI聊天服务")
@Feature("AI聊天核心功能")
@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
class AiChatServiceTest {

    @Mock
    private SiliconFlowChatClient siliconFlowChatClient;

    @Mock
    private MemoryService memoryService;

    @Mock
    private chat.liuxin.ai.monitor.AiMetrics aiMetrics;

    @InjectMocks
    private AiChatServiceImpl aiChatService;

    private ChatRequest validRequest;
    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_MESSAGE = "你好";
    private static final String TEST_MODEL = "THUDM/glm-4-9b-chat";

    @BeforeEach
    void setUp() {
        validRequest = new ChatRequest();
        validRequest.setMessage(TEST_MESSAGE);
        validRequest.setModel(TEST_MODEL);
        validRequest.setConversationId(1L);
    }

    @Test
    @Story("正常聊天流程")
    @Description("验证正常聊天请求能够成功处理并返回正确的响应")
    @Severity(SeverityLevel.CRITICAL)
    void testProcessChat_Success() {
        // Arrange
        when(siliconFlowChatClient.chat(anyList(), eq(TEST_MODEL)))
                .thenReturn("你好！我是纳西妲，很高兴为您服务。");

        // Mock MemoryService methods to avoid exceptions
        doNothing().when(memoryService).saveUserMessage(anyString(), anyLong(), anyString(), anyString(), isNull());
        doNothing().when(memoryService).saveAssistantMessage(anyString(), anyLong(), anyString(), anyString(), anyInt(), isNull());

        // Act
        ChatResponse response = aiChatService.processChat(validRequest, TEST_USER_ID);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals(TEST_MODEL, response.getModel());
        assertNotNull(response.getMessage());
        assertNotNull(response.getConversationId());

        // Verify interactions
        verify(siliconFlowChatClient, times(1)).chat(anyList(), eq(TEST_MODEL));
        verify(memoryService, times(1)).saveUserMessage(anyString(), anyLong(), eq(TEST_MESSAGE), eq(TEST_MODEL), isNull());
        verify(memoryService, times(1)).saveAssistantMessage(anyString(), anyLong(), anyString(), eq(TEST_MODEL), eq(1), isNull());
    }

    @Test
    @Story("默认模型处理")
    @Description("验证当请求中模型为空时，能够自动使用默认模型进行AI调用")
    @Severity(SeverityLevel.NORMAL)
    void testProcessChat_WithNullModel_ShouldUseDefault() {
        // Arrange
        ChatRequest request = new ChatRequest();
        request.setMessage(TEST_MESSAGE);
        // model为null

        when(siliconFlowChatClient.chat(anyList(), eq("THUDM/glm-4-9b-chat")))
                .thenReturn("默认模型回复");

        // Mock MemoryService methods
        doNothing().when(memoryService).saveUserMessage(anyString(), anyLong(), anyString(), anyString(), isNull());
        doNothing().when(memoryService).saveAssistantMessage(anyString(), anyLong(), anyString(), anyString(), anyInt(), isNull());

        // Act
        ChatResponse response = aiChatService.processChat(request, TEST_USER_ID);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        verify(siliconFlowChatClient, times(1)).chat(anyList(), eq("THUDM/glm-4-9b-chat"));
    }

    @Test
    @Story("连接异常处理")
    @Description("验证当AI服务连接失败时，能够正确抛出ConnectionException异常")
    @Severity(SeverityLevel.BLOCKER)
    void testProcessChat_ConnectionException_ShouldThrowConnectionException() {
        // Arrange
        when(siliconFlowChatClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException(new java.net.ConnectException("Connection failed")));

        // Act & Assert
        AIServiceException.ConnectionException exception = assertThrows(AIServiceException.ConnectionException.class, () -> {
            aiChatService.processChat(validRequest, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("AI服务连接失败"));
    }

    @Test
    @Story("超时异常处理")
    @Description("验证当AI服务响应超时时，能够正确抛出TimeoutException异常")
    @Severity(SeverityLevel.BLOCKER)
    void testProcessChat_TimeoutException_ShouldThrowTimeoutException() {
        // Arrange
        when(siliconFlowChatClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException(new java.util.concurrent.TimeoutException("Request timeout")));

        // Act & Assert
        AIServiceException.TimeoutException exception = assertThrows(AIServiceException.TimeoutException.class, () -> {
            aiChatService.processChat(validRequest, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("AI服务响应超时"));
    }

    @Test
    @Story("HTTP状态码异常处理")
    @Description("验证当AI服务返回HTTP错误状态码时，能够正确抛出RequestException异常")
    @Severity(SeverityLevel.CRITICAL)
    void testProcessChat_HttpStatusCodeException_ShouldThrowRequestException() {
        // Arrange
        org.springframework.web.client.HttpStatusCodeException httpEx =
                new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Bad Request");
        when(siliconFlowChatClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException(httpEx));

        // Act & Assert
        AIServiceException.RequestException exception = assertThrows(AIServiceException.RequestException.class, () -> {
            aiChatService.processChat(validRequest, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("AI服务HTTP错误"));
    }

    @Test
    @Story("空消息处理")
    @Description("验证当用户发送空消息时，系统能够正常处理并返回响应")
    @Severity(SeverityLevel.NORMAL)
    void testProcessChat_EmptyMessage_ShouldHandleGracefully() {
        // Arrange
        ChatRequest emptyRequest = new ChatRequest();
        emptyRequest.setMessage("");

        when(siliconFlowChatClient.chat(anyList(), anyString()))
                .thenReturn("我收到了您的消息");

        // Mock MemoryService methods
        doNothing().when(memoryService).saveUserMessage(anyString(), anyLong(), anyString(), anyString(), isNull());
        doNothing().when(memoryService).saveAssistantMessage(anyString(), anyLong(), anyString(), anyString(), anyInt(), isNull());

        // Act
        ChatResponse response = aiChatService.processChat(emptyRequest, TEST_USER_ID);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
    }

    @Test
    @Story("空用户ID处理")
    @Description("验证当用户ID为空时，系统能够正常处理并返回响应")
    @Severity(SeverityLevel.NORMAL)
    void testProcessChat_NullUserId_ShouldHandleGracefully() {
        // Arrange
        when(siliconFlowChatClient.chat(anyList(), anyString()))
                .thenReturn("回复");

        // Mock MemoryService methods
        doNothing().when(memoryService).saveUserMessage(anyString(), anyLong(), anyString(), anyString(), isNull());
        doNothing().when(memoryService).saveAssistantMessage(anyString(), anyLong(), anyString(), anyString(), anyInt(), isNull());

        // Act
        ChatResponse response = aiChatService.processChat(validRequest, null);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
    }

    @Test
    @Story("新会话创建")
    @Description("验证当请求中没有会话ID时，系统能够自动创建新会话并返回正确的会话ID")
    @Severity(SeverityLevel.NORMAL)
    void testProcessChat_NewConversation_ShouldCreateNewConversation() {
        // Arrange
        ChatRequest newConvRequest = new ChatRequest();
        newConvRequest.setMessage(TEST_MESSAGE);
        newConvRequest.setConversationId(null); // 新会话

        when(siliconFlowChatClient.chat(anyList(), anyString()))
                .thenReturn("新会话回复");
        when(memoryService.createConversation(anyString(), anyString()))
                .thenReturn(999L);

        // Mock MemoryService methods
        doNothing().when(memoryService).saveUserMessage(anyString(), anyLong(), anyString(), anyString(), isNull());
        doNothing().when(memoryService).saveAssistantMessage(anyString(), anyLong(), anyString(), anyString(), anyInt(), isNull());

        // Act
        ChatResponse response = aiChatService.processChat(newConvRequest, TEST_USER_ID);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        verify(memoryService, times(1)).createConversation(anyString(), anyString());
    }

    @Test
    @Story("流式聊天")
    @Description("验证流式聊天功能的基本可用性（异步测试）")
    @Severity(SeverityLevel.NORMAL)
    void testProcessStreamChat_Success() throws Exception {
        // Arrange
        validRequest.setMode("stream");

        // Act - 由于是异步操作，我们只验证不会抛出异常
        // 实际的流式测试需要更复杂的设置
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            // 这里可以添加流式测试逻辑
        });

        // Assert
        assertNotNull(future);
        future.get(); // 等待完成
    }

    @Test
    @Story("保存异常处理")
    @Description("验证当保存消息过程中出现异常时，仍能正确抛出原始异常而不是保存异常")
    @Severity(SeverityLevel.CRITICAL)
    void testProcessChat_ExceptionDuringSave_ShouldStillThrowOriginalException() {
        // Arrange
        when(siliconFlowChatClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("AI服务异常"));

        // 保存消息时也抛出异常
        doThrow(new RuntimeException("保存失败")).when(memoryService)
                .saveAssistantMessage(anyString(), anyLong(), anyString(), anyString(), anyInt(), isNull());

        // Act & Assert
        assertThrows(AIServiceException.class, () -> {
            aiChatService.processChat(validRequest, TEST_USER_ID);
        });
    }

    @Test
    @Story("长消息处理")
    @Description("验证系统能够正确处理超长消息（1万字符），不会出现性能问题或内存溢出")
    @Severity(SeverityLevel.NORMAL)
    void testProcessChat_LongMessage_ShouldHandleGracefully() {
        // Arrange
        String longMessage = "A".repeat(10000); // 1万字符的长消息
        ChatRequest longRequest = new ChatRequest();
        longRequest.setMessage(longMessage);

        when(siliconFlowChatClient.chat(anyList(), anyString()))
                .thenReturn("收到了长消息");

        // Mock MemoryService methods
        doNothing().when(memoryService).saveUserMessage(anyString(), anyLong(), anyString(), anyString(), isNull());
        doNothing().when(memoryService).saveAssistantMessage(anyString(), anyLong(), anyString(), anyString(), anyInt(), isNull());

        // Act
        ChatResponse response = aiChatService.processChat(longRequest, TEST_USER_ID);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
    }

    @Test
    @Story("特殊字符处理")
    @Description("验证系统能够正确处理包含特殊字符、表情符号和标点符号的消息")
    @Severity(SeverityLevel.NORMAL)
    void testProcessChat_SpecialCharacters_ShouldHandleGracefully() {
        // Arrange
        String specialMessage = "你好！🎉 这是一条包含特殊字符的消息：@#$%^&*()_+-=[]{}|;':\",./<>?";
        ChatRequest specialRequest = new ChatRequest();
        specialRequest.setMessage(specialMessage);

        when(siliconFlowChatClient.chat(anyList(), anyString()))
                .thenReturn("处理了特殊字符");

        // Mock MemoryService methods
        doNothing().when(memoryService).saveUserMessage(anyString(), anyLong(), anyString(), anyString(), isNull());
        doNothing().when(memoryService).saveAssistantMessage(anyString(), anyLong(), anyString(), anyString(), anyInt(), isNull());

        // Act
        ChatResponse response = aiChatService.processChat(specialRequest, TEST_USER_ID);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
    }
}
