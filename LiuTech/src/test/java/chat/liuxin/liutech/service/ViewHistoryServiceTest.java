package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.UserViewHistoryMapper;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostListResp;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ViewHistoryService 单元测试
 * 覆盖 recordView、getViewHistory、clearViewHistory 的核心逻辑和异常路径
 */
@ExtendWith(MockitoExtension.class)
class ViewHistoryServiceTest {

    @Mock
    private UserViewHistoryMapper userViewHistoryMapper;

    @Mock
    private PostsMapper postsMapper;

    @InjectMocks
    private ViewHistoryService viewHistoryService;

    // ========== recordView 测试 ==========

    @Test
    void recordView_shouldUpsertWhenPostExists() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = new Posts();
        post.setId(postId);
        post.setStatus("published");

        when(postsMapper.selectById(postId)).thenReturn(post);
        when(userViewHistoryMapper.upsertViewHistory(userId, postId)).thenReturn(1);

        viewHistoryService.recordView(postId, userId);

        verify(userViewHistoryMapper).upsertViewHistory(userId, postId);
    }

    @Test
    void recordView_shouldThrowWhenPostNotExists() {
        Long postId = 99L;
        Long userId = 10L;

        when(postsMapper.selectById(postId)).thenReturn(null);

        assertThrows(BusinessException.class, () -> viewHistoryService.recordView(postId, userId));
        verify(userViewHistoryMapper, never()).upsertViewHistory(any(), any());
    }

    // ========== getViewHistory 测试 ==========
    @Test
    void recordView_shouldRejectDraft() {
        Posts post = new Posts();
        post.setStatus("draft");
        when(postsMapper.selectById(1L)).thenReturn(post);
        assertThrows(BusinessException.class, () -> viewHistoryService.recordView(1L, 10L));
        verify(userViewHistoryMapper, never()).upsertViewHistory(any(), any());
    }

    @Test
    void getViewHistory_shouldReturnPagedRecords() {
        Long userId = 10L;
        PostListResp resp = new PostListResp();
        resp.setId(1L);
        resp.setViewedAt(new java.util.Date());

        Page<PostListResp> resultPage = new Page<>(1, 10, 1);
        resultPage.setRecords(List.of(resp));
        // service 内部 new Page 构造，不能按实例匹配，用 any() 匹配分页参数
        when(userViewHistoryMapper.selectViewHistory(any(Page.class), eq(userId))).thenReturn(resultPage);

        PageResp<PostListResp> result = viewHistoryService.getViewHistory(1, 10, userId);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getId());
    }

    // ========== clearViewHistory 测试 ==========

    @Test
    void clearViewHistory_shouldDeleteByUserId() {
        Long userId = 10L;
        when(userViewHistoryMapper.deleteByUserId(userId)).thenReturn(3);

        int cleared = viewHistoryService.clearViewHistory(userId);

        assertEquals(3, cleared);
        verify(userViewHistoryMapper).deleteByUserId(userId);
    }
}
