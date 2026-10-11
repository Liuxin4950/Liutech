package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.model.Comments;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommentsAdminServiceTest {
    private CommentsMapper comments;
    private CommentsAdminService service;

    @BeforeEach
    void setUp() {
        comments = mock(CommentsMapper.class);
        service = new CommentsAdminService(comments);
    }

    @Test
    void threadIncludesDeletedParentsSiblingsDescendantsAndOtherRootsWithinSamePost() {
        Comments parent = comment(1L, null); parent.setDeletedAt(new Date());
        Comments selected = comment(2L, 1L);
        Comments botSibling = comment(3L, 1L); botSibling.setBotId(8L);
        Comments descendant = comment(4L, 2L);
        Comments anotherRoot = comment(5L, null);
        List<Comments> all = List.of(parent, selected, botSibling, descendant, anotherRoot);
        when(comments.selectCommentsForAdminById(2L)).thenReturn(selected);
        when(comments.selectPostCommentMaxIdForAdmin(7L)).thenReturn(5L);
        when(comments.selectPostCommentsForAdmin(7L, 5L, 0L, 500)).thenReturn(all);
        when(comments.countPostCommentsForAdmin(7L, 5L)).thenReturn(5L);

        var thread = service.getCommentThread(2L, 1, 500, null);
        assertEquals(7L, thread.postId());
        assertEquals(2L, thread.commentId());
        assertEquals(5L, thread.snapshotMaxId());
        assertEquals(all, thread.comments().getRecords());
        assertNotNull(thread.comments().getRecords().getFirst().getDeletedAt());
        assertEquals("BOT", thread.comments().getRecords().get(2).getAuthorType());
        verify(comments, never()).selectPublicCommentById(anyLong());
        verify(comments, never()).selectThreadCommentsForAdmin(anyLong(), anyLong(), anyInt());
    }

    @Test
    void laterThreadPagesKeepInitialMaximumIdAndUseLongOffsetWithoutLatestReplyTruncation() {
        when(comments.selectCommentsForAdminById(2L)).thenReturn(comment(2L, 1L));
        when(comments.selectPostCommentsForAdmin(7L, 900L, 500L, 500)).thenReturn(List.of(comment(501L, 1L)));
        when(comments.countPostCommentsForAdmin(7L, 900L)).thenReturn(501L);
        var thread = service.getCommentThread(2L, 2, 500, 900L);
        assertEquals(2L, thread.comments().getCurrent());
        assertEquals(2L, thread.comments().getPages());
        assertEquals(501L, thread.comments().getTotal());
        verify(comments, never()).selectPostCommentMaxIdForAdmin(anyLong());
        verify(comments).selectPostCommentsForAdmin(7L, 900L, 500L, 500);
    }

    @Test
    void threadRejectsMissingCommentsInvalidPaginationAndSnapshotBeforeSelectedNode() {
        assertEquals(ErrorCode.COMMENT_NOT_FOUND.getCode(), assertThrows(BusinessException.class,
                () -> service.getCommentThread(99L, 1, 500, null)).getCode());
        assertThrows(BusinessException.class, () -> service.getCommentThread(2L, 0, 500, null));
        assertThrows(BusinessException.class, () -> service.getCommentThread(2L, 1, 501, null));
        when(comments.selectCommentsForAdminById(2L)).thenReturn(comment(2L, 1L));
        assertThrows(BusinessException.class, () -> service.getCommentThread(2L, 1, 500, 1L));
        verify(comments, never()).selectPostCommentsForAdmin(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void threadSqlBindsPostSnapshotAndPagingAndPreservesDeletedParentPreviewAndAuthorIdentity() throws Exception {
        var configuration = new MybatisConfiguration();
        String resource = "mapper/CommentsMapper.xml";
        try (var input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var bound = configuration.getMappedStatement(CommentsMapper.class.getName() + ".selectPostCommentsForAdmin")
                .getBoundSql(Map.of("postId", 7L, "snapshotMaxId", 900L, "offset", 500L, "limit", 500));
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("WHERE c.post_id = ? AND c.id <= ? ORDER BY c.id ASC LIMIT ?, ?"), sql);
        assertTrue(sql.contains("LEFT JOIN comments parent ON c.parent_id = parent.id"), sql);
        assertTrue(sql.contains("LEFT JOIN community_bots parent_bot"), sql);
        assertTrue(sql.contains("parent.deleted_at AS parent_deleted_at"), sql);
        assertFalse(sql.contains("deleted_at IS NULL"), sql);
        assertFalse(sql.contains("published"), sql);
        assertFalse(sql.contains("WITH RECURSIVE"), sql);
        assertEquals(4, bound.getParameterMappings().size());
    }

    private Comments comment(Long id, Long parentId) {
        Comments comment = new Comments();
        comment.setId(id); comment.setPostId(7L); comment.setPostTitle("测试文章");
        comment.setParentId(parentId); comment.setContent("评论" + id);
        return comment;
    }

    @Test
    void singlePermanentDeleteRemovesDeepestDescendantsBeforePhysicallyDeletingSelf() {
        when(comments.selectAllDescendantIds(List.of(1L))).thenReturn(List.of(3L, 2L));
        when(comments.permanentDeleteByIds(anyList())).thenReturn(1);
        assertTrue(service.permanentDeleteComment(1L));
        var order = inOrder(comments);
        order.verify(comments).selectAllDescendantIds(List.of(1L));
        order.verify(comments).permanentDeleteByIds(List.of(3L));
        order.verify(comments).permanentDeleteByIds(List.of(2L));
        order.verify(comments).permanentDeleteByIds(List.of(1L));
        verify(comments, never()).deleteById(anyLong());
        verify(comments, never()).selectPublicCommentById(anyLong());
    }

    @Test
    void permanentDeleteWithoutDescendantsStillUsesPhysicalDeleteForSelf() {
        when(comments.selectAllDescendantIds(List.of(1L))).thenReturn(List.of());
        when(comments.permanentDeleteByIds(List.of(1L))).thenReturn(1);
        assertTrue(service.permanentDeleteComment(1L));
        verify(comments).permanentDeleteByIds(List.of(1L));
        verify(comments, never()).deleteById(anyLong());
        verify(comments, never()).selectById(anyLong());
    }

    @Test
    void batchPermanentDeleteSupportsOverlappingAncestorsAndRoots() {
        List<Long> roots = List.of(1L, 2L);
        when(comments.selectAllDescendantIds(roots)).thenReturn(List.of(3L, 2L));
        when(comments.permanentDeleteByIds(anyList())).thenReturn(1);
        assertTrue(service.batchPermanentDeleteComments(roots));
        var order = inOrder(comments);
        order.verify(comments).selectAllDescendantIds(roots);
        order.verify(comments).permanentDeleteByIds(List.of(3L));
        order.verify(comments).permanentDeleteByIds(List.of(2L));
        order.verify(comments).permanentDeleteByIds(roots);
        verify(comments, never()).deleteById(anyLong());
        verify(comments, never()).selectPublicCommentById(anyLong());
    }

    @Test
    void failedDescendantDeleteStopsBeforeDeletingParent() {
        when(comments.selectAllDescendantIds(List.of(1L))).thenReturn(List.of(3L, 2L));
        when(comments.permanentDeleteByIds(List.of(3L))).thenThrow(new IllegalStateException("数据库失败"));
        assertThrows(RuntimeException.class, () -> service.permanentDeleteComment(1L));
        verify(comments, never()).permanentDeleteByIds(List.of(2L));
        verify(comments, never()).permanentDeleteByIds(List.of(1L));
    }

    @Test
    void invalidInputsDoNotExecuteDeleteAndMissingCommentReturnsFalse() {
        assertFalse(service.permanentDeleteComment(null));
        assertFalse(service.batchPermanentDeleteComments(null));
        assertFalse(service.batchPermanentDeleteComments(List.of()));
        verifyNoInteractions(comments);
        when(comments.selectAllDescendantIds(List.of(99L))).thenReturn(List.of());
        assertFalse(service.permanentDeleteComment(99L));
    }
}
