package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.CreateCommentReq;
import chat.liuxin.liutech.resp.CommentResp;
import chat.liuxin.liutech.service.CommentsService;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentsControllerTest {

    private CommentsController controller;
    private CommentsService commentsService;
    private UserUtils userUtils;

    @BeforeEach
    void setUp() {
        commentsService = mock(CommentsService.class);
        userUtils = mock(UserUtils.class);
        controller = new CommentsController(commentsService, userUtils);
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

        when(userUtils.getCurrentUserId()).thenReturn(1L);
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

        when(userUtils.getCurrentUserId()).thenReturn(null);

        Result<CommentResp> result = controller.createComment(req);

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
        assertNull(result.getData());
    }

    @Test
    void createComment_shouldFailWhenServiceThrows() {
        CreateCommentReq req = new CreateCommentReq();
        req.setPostId(1L);
        req.setContent("Test");

        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(commentsService.createComment(req)).thenThrow(new RuntimeException("文章不存在"));

        Result<CommentResp> result = controller.createComment(req);

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
    }
}
