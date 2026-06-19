package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.CreateCommentReq;
import chat.liuxin.liutech.resp.CommentResp;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentsServiceTest {

    private CommentsService commentsService;
    private CommentsMapper commentsMapper;
    private UserUtils userUtils;

    @BeforeEach
    void setUp() {
        commentsMapper = mock(CommentsMapper.class);
        userUtils = mock(UserUtils.class);
        commentsService = new CommentsService(commentsMapper, userUtils);
        // ServiceImpl 需要 baseMapper 字段
        ReflectionTestUtils.setField(commentsService, "baseMapper", commentsMapper);
    }

    // ========== getTopLevelCommentsByPostId ==========

    @Test
    void getTopLevelCommentsByPostId_shouldReturnTreeStructure() {
        Comments top1 = createComment(1L, 100L, null, "top comment 1");
        Comments top2 = createComment(2L, 100L, null, "top comment 2");
        Comments child1 = createComment(3L, 100L, 1L, "reply to top1");
        Comments grandchild = createComment(4L, 100L, 3L, "reply to child1");

        Users user = createUser(10L, "testuser", "http://avatar.png");
        top1.setUser(user);
        top2.setUser(user);
        child1.setUser(user);
        grandchild.setUser(user);

        when(commentsMapper.selectTopLevelCommentsByPostId(100L))
                .thenReturn(Arrays.asList(top1, top2));
        when(commentsMapper.selectAllDescendantsByRootIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(child1, grandchild));

        List<CommentResp> result = commentsService.getTopLevelCommentsByPostId(100L);

        assertEquals(2, result.size());
        // top1 should have child1, which has grandchild
        CommentResp top1Resp = result.get(0);
        assertEquals("top comment 1", top1Resp.getContent());
        assertNotNull(top1Resp.getChildren());
        assertEquals(1, top1Resp.getChildren().size());
        assertEquals("reply to top1", top1Resp.getChildren().get(0).getContent());
        assertNotNull(top1Resp.getChildren().get(0).getChildren());
        assertEquals(1, top1Resp.getChildren().get(0).getChildren().size());
        assertEquals("reply to child1", top1Resp.getChildren().get(0).getChildren().get(0).getContent());
        // top2 should have no children
        CommentResp top2Resp = result.get(1);
        assertEquals("top comment 2", top2Resp.getContent());
        assertNull(top2Resp.getChildren());
    }

    @Test
    void getTopLevelCommentsByPostId_shouldReturnEmptyListWhenNoComments() {
        when(commentsMapper.selectTopLevelCommentsByPostId(999L))
                .thenReturn(Collections.emptyList());

        List<CommentResp> result = commentsService.getTopLevelCommentsByPostId(999L);

        assertTrue(result.isEmpty());
        verify(commentsMapper, never()).selectAllDescendantsByRootIds(any());
    }

    // ========== createComment ==========

    @Test
    void createComment_shouldCreateCommentSuccessfully() {
        Users currentUser = createUser(1L, "testuser", "http://avatar.png");
        when(userUtils.getCurrentUser()).thenReturn(currentUser);

        CreateCommentReq req = new CreateCommentReq();
        req.setPostId(100L);
        req.setContent("Nice article!");

        // Mock save via baseMapper.insert (ServiceImpl.save delegates to insert)
        when(commentsMapper.insert(any(Comments.class))).thenReturn(1);

        CommentResp result = commentsService.createComment(req);

        assertNotNull(result);
        assertEquals("Nice article!", result.getContent());
        assertEquals(100L, result.getPostId());
        assertNotNull(result.getUser());
        assertEquals("testuser", result.getUser().getUsername());
        assertNull(result.getParentId());

        verify(commentsMapper).insert(argThat((Comments c) ->
                c.getPostId().equals(100L)
                        && c.getContent().equals("Nice article!")
                        && c.getUserId().equals(1L)
                        && c.getParentId() == null
        ));
    }

    @Test
    void createComment_shouldCreateReplyWithParentId() {
        Users currentUser = createUser(1L, "testuser", "http://avatar.png");
        when(userUtils.getCurrentUser()).thenReturn(currentUser);

        Comments parentComment = createComment(50L, 100L, null, "parent");
        when(commentsMapper.selectById(50L)).thenReturn(parentComment);
        when(commentsMapper.insert(any(Comments.class))).thenReturn(1);

        CreateCommentReq req = new CreateCommentReq();
        req.setPostId(100L);
        req.setContent("This is a reply");
        req.setParentId(50L);

        CommentResp result = commentsService.createComment(req);

        assertNotNull(result);
        assertEquals("This is a reply", result.getContent());
        assertEquals(50L, result.getParentId());

        verify(commentsMapper).insert(argThat((Comments c) ->
                c.getParentId().equals(50L) && c.getUserId().equals(1L)
        ));
    }

    @Test
    void createComment_shouldThrowWhenUserIsNull() {
        when(userUtils.getCurrentUser()).thenReturn(null);

        CreateCommentReq req = new CreateCommentReq();
        req.setPostId(100L);
        req.setContent("test");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentsService.createComment(req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void createComment_shouldThrowWhenParentCommentNotFound() {
        Users currentUser = createUser(1L, "testuser", "http://avatar.png");
        when(userUtils.getCurrentUser()).thenReturn(currentUser);
        when(commentsMapper.selectById(999L)).thenReturn(null);

        CreateCommentReq req = new CreateCommentReq();
        req.setPostId(100L);
        req.setContent("reply");
        req.setParentId(999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentsService.createComment(req));
        assertEquals(ErrorCode.PARENT_COMMENT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void createComment_shouldThrowWhenParentBelongsToDifferentPost() {
        Users currentUser = createUser(1L, "testuser", "http://avatar.png");
        when(userUtils.getCurrentUser()).thenReturn(currentUser);

        Comments parentComment = createComment(50L, 200L, null, "parent on post 200");
        when(commentsMapper.selectById(50L)).thenReturn(parentComment);

        CreateCommentReq req = new CreateCommentReq();
        req.setPostId(100L);  // different post
        req.setContent("reply");
        req.setParentId(50L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentsService.createComment(req));
        assertEquals(ErrorCode.PARENT_COMMENT_MISMATCH.getCode(), ex.getCode());
    }

    // ========== helper methods ==========

    private Comments createComment(Long id, Long postId, Long parentId, String content) {
        Comments comment = new Comments();
        comment.setId(id);
        comment.setPostId(postId);
        comment.setParentId(parentId);
        comment.setContent(content);
        comment.setUserId(1L);
        comment.setCreatedAt(new Date());
        comment.setUpdatedAt(new Date());
        return comment;
    }

    private Users createUser(Long id, String username, String avatarUrl) {
        Users user = new Users();
        user.setId(id);
        user.setUsername(username);
        user.setAvatarUrl(avatarUrl);
        return user;
    }
}
