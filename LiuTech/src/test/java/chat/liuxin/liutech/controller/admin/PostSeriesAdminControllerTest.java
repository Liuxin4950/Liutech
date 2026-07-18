package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostSeriesResp;
import chat.liuxin.liutech.service.PostSeriesService;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostSeriesAdminController 单元测试
 * 类级 @PreAuthorize 已保证认证，异常由 GlobalExceptionHandler 兜底，Controller 直接传播。
 *
 * @author 刘鑫
 */
class PostSeriesAdminControllerTest {

    private PostSeriesAdminController controller;
    private PostSeriesService postSeriesService;
    private UserUtils userUtils;

    @BeforeEach
    void setUp() {
        controller = new PostSeriesAdminController();
        postSeriesService = mock(PostSeriesService.class);
        userUtils = mock(UserUtils.class);
        ReflectionTestUtils.setField(controller, "postSeriesService", postSeriesService);
        ReflectionTestUtils.setField(controller, "userUtils", userUtils);
    }

    @Test
    void getSeriesList_shouldReturnPage() {
        PageResp<PostSeriesResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(postSeriesService.getSeriesListForAdmin(1, 10, null, false)).thenReturn(pageResp);

        Result<PageResp<PostSeriesResp>> result = controller.getSeriesList(1, 10, null, false);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void getSeriesById_shouldReturnWhenExists() {
        PostSeriesResp s = new PostSeriesResp();
        s.setName("Spring");
        when(postSeriesService.getSeriesDetail(1L)).thenReturn(s);

        Result<PostSeriesResp> result = controller.getSeriesById(1L);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void getSeriesById_shouldReturnErrorWhenNotFound() {
        when(postSeriesService.getSeriesDetail(999L)).thenReturn(null);
        Result<PostSeriesResp> result = controller.getSeriesById(999L);
        assertEquals(ErrorCode.SERIES_NOT_FOUND.getCode(), result.getCode());
    }

    @Test
    void createSeries_shouldReturnSuccess() {
        PostSeriesResp s = new PostSeriesResp();
        s.setName("Spring");
        when(postSeriesService.save(any())).thenReturn(true);

        Result<String> result = controller.createSeries(s);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void createSeries_shouldReturnErrorWhenServiceFails() {
        when(postSeriesService.save(any())).thenReturn(false);
        Result<String> result = controller.createSeries(new PostSeriesResp());
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    @Test
    void updateSeries_shouldReturnSuccess() {
        when(postSeriesService.updateById(any())).thenReturn(true);
        Result<String> result = controller.updateSeries(1L, new PostSeriesResp());
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void deleteSeries_shouldReturnSuccess() {
        when(postSeriesService.removeByIds(List.of(1L))).thenReturn(true);
        Result<String> result = controller.deleteSeries(1L);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void restoreSeries_shouldReturnSuccess() {
        when(postSeriesService.restoreSeries(1L)).thenReturn(true);
        Result<String> result = controller.restoreSeries(1L);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void permanentDeleteSeries_shouldReturnSuccess() {
        when(postSeriesService.permanentDeleteSeries(1L)).thenReturn(true);
        Result<String> result = controller.permanentDeleteSeries(1L);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void updatePostsOrder_shouldPassSeriesIdAndOperator() {
        when(userUtils.getCurrentUserId()).thenReturn(10L);
        when(postSeriesService.batchUpdateSeriesSort(eq(1L), anyList(), eq(10L))).thenReturn(true);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("postId", 100L);
        item.put("seriesSort", 0);
        items.add(item);

        Result<String> result = controller.updatePostsOrder(1L, items);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(postSeriesService).batchUpdateSeriesSort(eq(1L), anyList(), eq(10L));
    }

    @Test
    void updatePostsOrder_shouldReturnErrorWhenServiceFails() {
        when(userUtils.getCurrentUserId()).thenReturn(10L);
        when(postSeriesService.batchUpdateSeriesSort(anyLong(), anyList(), anyLong())).thenReturn(false);

        Result<String> result = controller.updatePostsOrder(1L, Collections.emptyList());

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }
}
