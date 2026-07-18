package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.PostSeriesResp;
import chat.liuxin.liutech.service.PostSeriesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PostSeriesController 单元测试（用户前台公开接口）
 *
 * @author 刘鑫
 */
class PostSeriesControllerTest {

    private PostSeriesController controller;
    private PostSeriesService postSeriesService;

    @BeforeEach
    void setUp() {
        controller = new PostSeriesController();
        postSeriesService = mock(PostSeriesService.class);
        ReflectionTestUtils.setField(controller, "postSeriesService", postSeriesService);
    }

    @Test
    void getSeriesList_shouldReturnList() {
        PostSeriesResp s = new PostSeriesResp();
        s.setName("Spring");
        when(postSeriesService.getAllSeriesWithPostCount()).thenReturn(List.of(s));

        Result<List<PostSeriesResp>> result = controller.getSeriesList();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("Spring", result.getData().get(0).getName());
    }

    @Test
    void getSeriesList_shouldReturnEmpty() {
        when(postSeriesService.getAllSeriesWithPostCount()).thenReturn(Collections.emptyList());
        Result<List<PostSeriesResp>> result = controller.getSeriesList();
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    void getSeriesById_shouldReturnWhenExists() {
        PostSeriesResp s = new PostSeriesResp();
        s.setName("Spring");
        when(postSeriesService.getSeriesDetail(1L)).thenReturn(s);

        Result<PostSeriesResp> result = controller.getSeriesById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Spring", result.getData().getName());
    }

    @Test
    void getSeriesById_shouldReturnErrorWhenNotFound() {
        when(postSeriesService.getSeriesDetail(999L)).thenReturn(null);

        Result<PostSeriesResp> result = controller.getSeriesById(999L);

        assertEquals(ErrorCode.SERIES_NOT_FOUND.getCode(), result.getCode());
        assertNull(result.getData());
    }
}
