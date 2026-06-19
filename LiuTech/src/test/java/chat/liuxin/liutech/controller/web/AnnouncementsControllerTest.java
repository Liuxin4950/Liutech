package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.AnnouncementResp;
import chat.liuxin.liutech.service.AnnouncementsService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnouncementsControllerTest {

    private AnnouncementsController controller;
    private AnnouncementsService announcementsService;

    @BeforeEach
    void setUp() {
        controller = new AnnouncementsController();
        announcementsService = mock(AnnouncementsService.class);
        ReflectionTestUtils.setField(controller, "announcementsService", announcementsService);
    }

    // ========== getValidAnnouncements ==========

    @Test
    void getValidAnnouncements_shouldReturnPage() {
        IPage<AnnouncementResp> page = new Page<>();
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(announcementsService.getValidAnnouncements(1, 10)).thenReturn(page);

        Result<IPage<AnnouncementResp>> result = controller.getValidAnnouncements(1, 10);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void getValidAnnouncements_shouldPassPaginationParams() {
        IPage<AnnouncementResp> page = new Page<>();
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(announcementsService.getValidAnnouncements(2, 5)).thenReturn(page);

        Result<IPage<AnnouncementResp>> result = controller.getValidAnnouncements(2, 5);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(announcementsService).getValidAnnouncements(2, 5);
    }

    // ========== getTopAnnouncements ==========

    @Test
    void getTopAnnouncements_shouldReturnList() {
        AnnouncementResp resp = new AnnouncementResp();
        resp.setTitle("System Notice");
        resp.setIsTop(1);
        when(announcementsService.getTopAnnouncements(3)).thenReturn(List.of(resp));

        Result<List<AnnouncementResp>> result = controller.getTopAnnouncements(3);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("System Notice", result.getData().get(0).getTitle());
    }

    @Test
    void getTopAnnouncements_shouldReturnEmptyListWhenNoneExist() {
        when(announcementsService.getTopAnnouncements(5)).thenReturn(Collections.emptyList());

        Result<List<AnnouncementResp>> result = controller.getTopAnnouncements(5);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== getLatestAnnouncements ==========

    @Test
    void getLatestAnnouncements_shouldReturnList() {
        AnnouncementResp resp = new AnnouncementResp();
        resp.setTitle("New Update");
        when(announcementsService.getLatestAnnouncements(5)).thenReturn(List.of(resp));

        Result<List<AnnouncementResp>> result = controller.getLatestAnnouncements(5);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("New Update", result.getData().get(0).getTitle());
    }

    @Test
    void getLatestAnnouncements_shouldReturnEmptyListWhenNoneExist() {
        when(announcementsService.getLatestAnnouncements(10)).thenReturn(Collections.emptyList());

        Result<List<AnnouncementResp>> result = controller.getLatestAnnouncements(10);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }
}
