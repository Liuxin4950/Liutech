package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.CommentThreadResp;
import chat.liuxin.liutech.service.CommentsAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

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
        commentsAdminService = mock(CommentsAdminService.class);
        controller = new CommentsAdminController(commentsAdminService);
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
    void getCommentList_shouldPropagateException() {
        when(commentsAdminService.getCommentListForAdmin(anyInt(), anyInt(), any(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("db error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getCommentList(1, 10, null, null, null, false));
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

    @Test
    void commentThreadPassesSnapshotAndPagingToServiceAndValidatesIds() {
        var thread = new CommentThreadResp(2L, 7L, "测试文章", 900L,
                new PageResp<Comments>(List.of(), 501L, 2L, 500L));
        when(commentsAdminService.getCommentThread(2L, 2, 500, 900L)).thenReturn(thread);
        assertSame(thread, controller.getCommentThread(2L, 2, 500, 900L).getData());
        assertThrows(BusinessException.class, () -> controller.getCommentThread(0L, 1, 500, null));
        assertThrows(BusinessException.class, () -> controller.getCommentThread(2L, 1, 500, 0L));
        verify(commentsAdminService).getCommentThread(2L, 2, 500, 900L);
        verifyNoMoreInteractions(commentsAdminService);
    }

    @Test
    void actualMethodSecurityRejectsAnonymousAndNormalUsersButAllowsAdminThreadReview() {
        try (var context = new AnnotationConfigApplicationContext(ThreadSecurityConfig.class)) {
            var securedController = context.getBean(CommentsAdminController.class);
            var service = context.getBean(CommentsAdminService.class);
            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class,
                    () -> securedController.getCommentThread(2L, 1, 500, null));
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", null, "ROLE_USER"));
            assertThrows(AccessDeniedException.class,
                    () -> securedController.getCommentThread(2L, 1, 500, null));
            verifyNoInteractions(service);
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));
            var thread = new CommentThreadResp(2L, 7L, "测试文章", 2L,
                    new PageResp<Comments>(List.of(), 0L, 1L, 500L));
            when(service.getCommentThread(2L, 1, 500, null)).thenReturn(thread);
            assertSame(thread, securedController.getCommentThread(2L, 1, 500, null).getData());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class ThreadSecurityConfig {
        @Bean CommentsMapper commentsMapper() { return mock(CommentsMapper.class); }
        @Bean CommentsAdminService commentsAdminService() { return mock(CommentsAdminService.class); }
        @Bean CommentsAdminController commentsAdminController(CommentsAdminService service) { return new CommentsAdminController(service); }
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
