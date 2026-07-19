package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Messages;
import chat.liuxin.liutech.service.MessagesService;
import chat.liuxin.liutech.utils.UserUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessagesAdminControllerTest {

    private MessagesAdminController controller;
    private MessagesService messagesService;
    private UserUtils userUtils;

    @BeforeEach
    void setUp() {
        messagesService = mock(MessagesService.class);
        userUtils = mock(UserUtils.class);
        controller = new MessagesAdminController(messagesService, userUtils);
    }

    // ========== getMessagesList ==========

    @Test
    void getMessagesList_shouldReturnPageResult() {
        Page<Messages> page = new Page<>(1, 10);
        page.setTotal(0);
        when(messagesService.getMessagesForAdmin(1, 10, null, null, false)).thenReturn(page);

        Result<IPage<Messages>> result = controller.getMessagesList(1, 10, null, null, false);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void getMessagesList_shouldPassFilterParams() {
        Page<Messages> page = new Page<>(1, 10);
        page.setTotal(3);
        when(messagesService.getMessagesForAdmin(1, 10, "test", 1, true)).thenReturn(page);

        Result<IPage<Messages>> result = controller.getMessagesList(1, 10, "test", 1, true);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(messagesService).getMessagesForAdmin(1, 10, "test", 1, true);
    }

    @Test
    void getMessagesList_shouldPropagateException() {
        when(messagesService.getMessagesForAdmin(anyInt(), anyInt(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("db error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getMessagesList(1, 10, null, null, false));
    }

    // ========== getMessageById ==========

    @Test
    void getMessageById_shouldReturnMessageWhenExists() {
        Messages message = new Messages();
        message.setNickname("Test User");
        when(messagesService.getById(1L)).thenReturn(message);

        Result<Messages> result = controller.getMessageById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Test User", result.getData().getNickname());
    }

    @Test
    void getMessageById_shouldReturnErrorWhenNotFound() {
        when(messagesService.getById(999L)).thenReturn(null);

        Result<Messages> result = controller.getMessageById(999L);

        assertEquals(ErrorCode.NOT_FOUND.getCode(), result.getCode());
    }

    @Test
    void getMessageById_shouldThrowWhenIdInvalid() {
        assertThrows(BusinessException.class, () -> controller.getMessageById(0L));
    }

    // ========== reviewMessage ==========

    @Test
    void reviewMessage_shouldReturnSuccess() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(messagesService.reviewMessage(1L, 1, 1L)).thenReturn(true);

        MessagesAdminController.ReviewRequest req = new MessagesAdminController.ReviewRequest();
        req.setStatus(1);
        Result<String> result = controller.reviewMessage(1L, req);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void reviewMessage_shouldReturnErrorWhenServiceFails() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(messagesService.reviewMessage(1L, 2, 1L)).thenReturn(false);

        MessagesAdminController.ReviewRequest req = new MessagesAdminController.ReviewRequest();
        req.setStatus(2);
        Result<String> result = controller.reviewMessage(1L, req);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    @Test
    void reviewMessage_shouldPropagateException() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(messagesService.reviewMessage(anyLong(), anyInt(), anyLong()))
                .thenThrow(new RuntimeException("error"));

        MessagesAdminController.ReviewRequest req = new MessagesAdminController.ReviewRequest();
        req.setStatus(1);
        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.reviewMessage(1L, req));
    }

    // ========== replyMessage ==========

    @Test
    void replyMessage_shouldReturnSuccess() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(messagesService.replyMessage(1L, "Thanks!", 1L)).thenReturn(true);

        MessagesAdminController.ReplyRequest req = new MessagesAdminController.ReplyRequest();
        req.setReply("Thanks!");
        Result<String> result = controller.replyMessage(1L, req);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void replyMessage_shouldReturnErrorWhenServiceFails() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(messagesService.replyMessage(anyLong(), any(), anyLong())).thenReturn(false);

        MessagesAdminController.ReplyRequest req = new MessagesAdminController.ReplyRequest();
        req.setReply("Reply");
        Result<String> result = controller.replyMessage(1L, req);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== deleteMessage ==========

    @Test
    void deleteMessage_shouldReturnSuccess() {
        when(messagesService.deleteMessage(1L)).thenReturn(true);

        Result<String> result = controller.deleteMessage(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void deleteMessage_shouldReturnErrorWhenServiceFails() {
        when(messagesService.deleteMessage(1L)).thenReturn(false);

        Result<String> result = controller.deleteMessage(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchDeleteMessages ==========

    @Test
    void batchDeleteMessages_shouldReturnSuccess() {
        when(messagesService.batchDeleteMessages(java.util.List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchDeleteMessages(java.util.List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchDeleteMessages_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchDeleteMessages(Collections.emptyList()));
    }

    // ========== restoreMessage ==========

    @Test
    void restoreMessage_shouldReturnSuccess() {
        when(messagesService.restoreMessage(1L)).thenReturn(true);

        Result<String> result = controller.restoreMessage(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void restoreMessage_shouldReturnErrorWhenServiceFails() {
        when(messagesService.restoreMessage(1L)).thenReturn(false);

        Result<String> result = controller.restoreMessage(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== permanentDeleteMessage ==========

    @Test
    void permanentDeleteMessage_shouldReturnSuccess() {
        when(messagesService.permanentDeleteMessage(1L)).thenReturn(true);

        Result<String> result = controller.permanentDeleteMessage(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void permanentDeleteMessage_shouldReturnErrorWhenServiceFails() {
        when(messagesService.permanentDeleteMessage(1L)).thenReturn(false);

        Result<String> result = controller.permanentDeleteMessage(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchPermanentDeleteMessages ==========

    @Test
    void batchPermanentDeleteMessages_shouldReturnSuccess() {
        when(messagesService.batchPermanentDeleteMessages(java.util.List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchPermanentDeleteMessages(java.util.List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchPermanentDeleteMessages_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchPermanentDeleteMessages(Collections.emptyList()));
    }
}
