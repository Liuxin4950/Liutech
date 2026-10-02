package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.CommentsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
