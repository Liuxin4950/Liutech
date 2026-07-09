package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.DashboardResp;
import chat.liuxin.liutech.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardControllerTest {

    private DashboardController controller;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        controller = new DashboardController();
        dashboardService = mock(DashboardService.class);

        ReflectionTestUtils.setField(controller, "dashboardService", dashboardService);
    }

    // ========== getDashboardStats ==========

    @Test
    void getDashboardStats_shouldReturnStats() {
        DashboardResp resp = new DashboardResp();
        DashboardResp.BasicStats basicStats = new DashboardResp.BasicStats();
        basicStats.setPostCount(10L);
        basicStats.setUserCount(5L);
        resp.setBasicStats(basicStats);
        when(dashboardService.getDashboardStats()).thenReturn(resp);

        Result<DashboardResp> result = controller.getDashboardStats();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(10L, result.getData().getBasicStats().getPostCount());
        assertEquals(5L, result.getData().getBasicStats().getUserCount());
    }

    @Test
    void getDashboardStats_shouldReturnEmptyStatsWhenNoData() {
        DashboardResp resp = new DashboardResp();
        when(dashboardService.getDashboardStats()).thenReturn(resp);

        Result<DashboardResp> result = controller.getDashboardStats();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void getDashboardStats_shouldPropagateException() {
        when(dashboardService.getDashboardStats()).thenThrow(new RuntimeException("db error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getDashboardStats());
    }
}
