package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.CreateMessageReq;
import chat.liuxin.liutech.resp.MessageResp;
import chat.liuxin.liutech.service.MessagesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagesControllerTest {

    private MessagesController controller;
    private MessagesService messagesService;

    @BeforeEach
    void setUp() {
        messagesService = mock(MessagesService.class);
        controller = new MessagesController(messagesService);
    }

    // ========== getPublicMessages ==========

    @Test
    void getPublicMessages_shouldReturnList() {
        MessageResp msg = new MessageResp();
        msg.setNickname("visitor");
        msg.setContent("Hello!");
        when(messagesService.getApprovedMessages()).thenReturn(List.of(msg));

        Result<List<MessageResp>> result = controller.getPublicMessages();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("visitor", result.getData().get(0).getNickname());
    }

    @Test
    void getPublicMessages_shouldReturnEmptyListWhenNoneExist() {
        when(messagesService.getApprovedMessages()).thenReturn(Collections.emptyList());

        Result<List<MessageResp>> result = controller.getPublicMessages();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== createMessage ==========

    @Test
    void createMessage_shouldSucceedWithValidData() {
        CreateMessageReq req = new CreateMessageReq();
        req.setNickname("testuser");
        req.setEmail("test@example.com");
        req.setContent("Hello world!");

        MessageResp resp = new MessageResp();
        resp.setId(1L);
        resp.setNickname("testuser");

        when(messagesService.createMessage(req)).thenReturn(resp);

        Result<MessageResp> result = controller.createMessage(req);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1L, result.getData().getId());
    }

    @Test
    void createMessage_shouldFailWhenServiceThrows() {
        CreateMessageReq req = new CreateMessageReq();
        req.setNickname("testuser");
        req.setEmail("test@example.com");
        req.setContent("Hello");

        when(messagesService.createMessage(req)).thenThrow(new RuntimeException("提交过于频繁"));

        Result<MessageResp> result = controller.createMessage(req);

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
    }
}
