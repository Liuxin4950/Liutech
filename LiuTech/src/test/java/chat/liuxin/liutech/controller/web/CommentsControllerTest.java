package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.CreateCommentReq;
import chat.liuxin.liutech.resp.CommentResp;
import chat.liuxin.liutech.service.CommentsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentsControllerTest {

    private CommentsController controller;
    private CommentsService commentsService;

    @BeforeEach
    void setUp() {
        commentsService = mock(CommentsService.class);
        controller = new CommentsController(commentsService);
    }

    // ========== getTreeCommentsByPostId ==========

    @Test
    void getTreeCommentsByPostId_shouldReturnComments() {
        CommentResp comment = new CommentResp();
        comment.setContent("Great post!");
        comment.setPostId(1L);
        when(commentsService.getTopLevelCommentsByPostId(1L)).thenReturn(List.of(comment));

        Result<List<CommentResp>> result = controller.getTreeCommentsByPostId(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("Great post!", result.getData().get(0).getContent());
    }

    @Test
    void getTreeCommentsByPostId_shouldReturnEmptyListWhenNoComments() {
        when(commentsService.getTopLevelCommentsByPostId(999L)).thenReturn(Collections.emptyList());

        Result<List<CommentResp>> result = controller.getTreeCommentsByPostId(999L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== createComment ==========

    @Test
    void createComment_shouldSucceedWhenLoggedIn() {
        CreateCommentReq req = new CreateCommentReq();
        req.setPostId(1L);
        req.setContent("Nice article!");

        CommentResp resp = new CommentResp();
        resp.setId(100L);
        resp.setContent("Nice article!");

        when(commentsService.createComment(req)).thenReturn(resp);

        Result<CommentResp> result = controller.createComment(req);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(100L, result.getData().getId());
        assertEquals("Nice article!", result.getData().getContent());
    }

    @Test
    void createComment_shouldFailWhenNotLoggedIn() {
        CreateCommentReq req = new CreateCommentReq();
        req.setPostId(1L);
        req.setContent("Test");

        when(commentsService.createComment(req)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
        var error = assertThrows(BusinessException.class, () -> controller.createComment(req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), error.getCode());
    }

    @Test
    void createComment_shouldFailWhenServiceThrows() {
        CreateCommentReq req = new CreateCommentReq();
        req.setPostId(1L);
        req.setContent("Test");

        when(commentsService.createComment(req)).thenThrow(new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "文章不存在"));

        // 已知业务错误保留错误码与消息，由 GlobalExceptionHandler 转响应
        BusinessException ex = assertThrows(BusinessException.class, () -> controller.createComment(req));

        assertEquals(ErrorCode.ARTICLE_NOT_FOUND.getCode(), ex.getCode());
        assertEquals("文章不存在", ex.getMessage());
    }
}
