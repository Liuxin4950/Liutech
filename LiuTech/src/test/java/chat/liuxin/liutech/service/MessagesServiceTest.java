package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.MessagesMapper;
import chat.liuxin.liutech.model.Messages;
import chat.liuxin.liutech.req.CreateMessageReq;
import chat.liuxin.liutech.resp.MessageResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagesServiceTest {

    private MessagesService messagesService;
    private MessagesMapper messagesMapper;

    @BeforeEach
    void setUp() {
        messagesService = new MessagesService();
        messagesMapper = mock(MessagesMapper.class);
        // MessagesService uses field injection (no @RequiredArgsConstructor),
        // inject mock into the inherited baseMapper field from ServiceImpl
        ReflectionTestUtils.setField(messagesService, "baseMapper", messagesMapper);
    }

    // ========== getApprovedMessages ==========

    @Test
    void getApprovedMessages_shouldReturnApprovedMessagesWithMaskedEmails() {
        Messages msg1 = createMessage(1L, "Alice", "alice@example.com", "Hello", 1);
        Messages msg2 = createMessage(2L, "Bob", "bob@test.org", "World", 1);

        when(messagesMapper.selectList(any())).thenReturn(Arrays.asList(msg1, msg2));

        List<MessageResp> result = messagesService.getApprovedMessages();

        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).getNickname());
        assertEquals("al***@example.com", result.get(0).getEmail());
        assertEquals("Hello", result.get(0).getContent());
        assertEquals(1, result.get(0).getStatus());
        assertEquals("Bob", result.get(1).getNickname());
        assertEquals("bo***@test.org", result.get(1).getEmail());
    }

    @Test
    void getApprovedMessages_shouldReturnEmptyListWhenNoMessages() {
        when(messagesMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<MessageResp> result = messagesService.getApprovedMessages();

        assertTrue(result.isEmpty());
    }

    @Test
    void getApprovedMessages_shouldMaskShortEmail() {
        // Email with <=2 chars before @
        Messages msg = createMessage(1L, "User", "ab@x.com", "content", 1);
        when(messagesMapper.selectList(any())).thenReturn(List.of(msg));

        List<MessageResp> result = messagesService.getApprovedMessages();

        assertEquals(1, result.size());
        assertEquals("**@x.com", result.get(0).getEmail());
    }

    // ========== createMessage ==========

    @Test
    void createMessage_shouldCreateMessageSuccessfully() {
        when(messagesMapper.selectCount(any())).thenReturn(0L);
        when(messagesMapper.insert(any(Messages.class))).thenReturn(1);

        CreateMessageReq req = new CreateMessageReq();
        req.setNickname("TestUser");
        req.setEmail("test@example.com");
        req.setContent("Hello World");

        MessageResp result = messagesService.createMessage(req);

        assertNotNull(result);
        assertEquals("TestUser", result.getNickname());
        assertEquals("te***@example.com", result.getEmail());
        assertEquals("Hello World", result.getContent());
        assertEquals(0, result.getStatus());

        verify(messagesMapper).insert(argThat((Messages m) ->
                m.getNickname().equals("TestUser")
                        && m.getEmail().equals("test@example.com")
                        && m.getContent().equals("Hello World")
                        && m.getStatus() == 0
        ));
    }

    @Test
    void createMessage_shouldThrowWhenEmailIsInvalid() {
        CreateMessageReq req = new CreateMessageReq();
        req.setNickname("TestUser");
        req.setEmail("not-an-email");
        req.setContent("Hello");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> messagesService.createMessage(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    void createMessage_shouldThrowWhenRateLimited() {
        when(messagesMapper.selectCount(any())).thenReturn(1L);

        CreateMessageReq req = new CreateMessageReq();
        req.setNickname("TestUser");
        req.setEmail("test@example.com");
        req.setContent("Hello");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> messagesService.createMessage(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("频繁"));
    }

    @Test
    void createMessage_shouldThrowWhenSaveFails() {
        when(messagesMapper.selectCount(any())).thenReturn(0L);
        when(messagesMapper.insert(any(Messages.class))).thenReturn(0);

        CreateMessageReq req = new CreateMessageReq();
        req.setNickname("TestUser");
        req.setEmail("test@example.com");
        req.setContent("Hello");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> messagesService.createMessage(req));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    // ========== helper methods ==========

    private Messages createMessage(Long id, String nickname, String email, String content, Integer status) {
        Messages msg = new Messages();
        msg.setId(id);
        msg.setNickname(nickname);
        msg.setEmail(email);
        msg.setContent(content);
        msg.setStatus(status);
        msg.setCreatedAt(new Date());
        msg.setUpdatedAt(new Date());
        return msg;
    }
}
