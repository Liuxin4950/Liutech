package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.CommentsAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommentsAdminControllerTest {

    private CommentsAdminController controller;
    private CommentsAdminService commentsAdminService;

    @BeforeEach
    void setUp() {
        controller = new CommentsAdminController();
        commentsAdminService = mock(CommentsAdminService.class);

        ReflectionTestUtils.setField(controller, "commentsAdminService", commentsAdminService);
    }

    // ========== getCommentList ==========

    @Test
    void getCommentList_shouldReturnPageResult() {
        PageResp<Comments> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(commentsAdminService.getCommentListForAdmin(1, 10, null, null, null, false)).thenReturn(pageResp);

        Result<PageResp<Comments>> result = controller.getCommentList(1, 10, null, null, null, false);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void getCommentList_shouldPassFilterParams() {
        PageResp<Comments> pageResp = new PageResp<>(Collections.emptyList(), 5L, 1L, 10L);
        when(commentsAdminService.getCommentListForAdmin(1, 10, 1L, 2L, "approved", true)).thenReturn(pageResp);

        Result<PageResp<Comments>> result = controller.getCommentList(1, 10, 1L, 2L, "approved", true);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(commentsAdminService).getCommentListForAdmin(1, 10, 1L, 2L, "approved", true);
    }

    @Test
    void getCommentList_shouldHandleException() {
        when(commentsAdminService.getCommentListForAdmin(anyInt(), anyInt(), any(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("db error"));

        Result<PageResp<Comments>> result = controller.getCommentList(1, 10, null, null, null, false);

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
    }

    // ========== getCommentById ==========

    @Test
    void getCommentById_shouldReturnCommentWhenExists() {
        Comments comment = new Comments();
        comment.setContent("Nice post");
        when(commentsAdminService.getCommentById(1L)).thenReturn(comment);

        Result<Comments> result = controller.getCommentById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Nice post", result.getData().getContent());
    }

    @Test
    void getCommentById_shouldReturnErrorWhenNotFound() {
        when(commentsAdminService.getCommentById(999L)).thenReturn(null);

        Result<Comments> result = controller.getCommentById(999L);

        assertEquals(ErrorCode.COMMENT_NOT_FOUND.getCode(), result.getCode());
    }

    @Test
    void getCommentById_shouldThrowWhenIdInvalid() {
        assertThrows(BusinessException.class, () -> controller.getCommentById(0L));
    }

    // ========== deleteComment ==========

    @Test
    void deleteComment_shouldReturnSuccess() {
        when(commentsAdminService.softDeleteComment(1L)).thenReturn(true);

        Result<String> result = controller.deleteComment(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void deleteComment_shouldReturnErrorWhenServiceFails() {
        when(commentsAdminService.softDeleteComment(1L)).thenReturn(false);

        Result<String> result = controller.deleteComment(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchDeleteComments ==========

    @Test
    void batchDeleteComments_shouldReturnSuccess() {
        when(commentsAdminService.batchSoftDeleteComments(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchDeleteComments(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchDeleteComments_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchDeleteComments(Collections.emptyList()));
    }

    // ========== restoreComment ==========

    @Test
    void restoreComment_shouldReturnSuccess() {
        when(commentsAdminService.restoreComment(1L)).thenReturn(true);

        Result<String> result = controller.restoreComment(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void restoreComment_shouldReturnErrorWhenServiceFails() {
        when(commentsAdminService.restoreComment(1L)).thenReturn(false);

        Result<String> result = controller.restoreComment(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== permanentDeleteComment ==========

    @Test
    void permanentDeleteComment_shouldReturnSuccess() {
        when(commentsAdminService.permanentDeleteComment(1L)).thenReturn(true);

        Result<String> result = controller.permanentDeleteComment(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void permanentDeleteComment_shouldReturnErrorWhenServiceFails() {
        when(commentsAdminService.permanentDeleteComment(1L)).thenReturn(false);

        Result<String> result = controller.permanentDeleteComment(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchPermanentDeleteComments ==========

    @Test
    void batchPermanentDeleteComments_shouldReturnSuccess() {
        when(commentsAdminService.batchPermanentDeleteComments(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchPermanentDeleteComments(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchPermanentDeleteComments_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchPermanentDeleteComments(Collections.emptyList()));
    }
}
