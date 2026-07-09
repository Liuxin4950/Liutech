package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.PointsTransaction;
import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PointsTransactionResp;
import chat.liuxin.liutech.resp.UserCheckinResp;
import chat.liuxin.liutech.service.PointsAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PointsAdminControllerTest {

    private PointsAdminController controller;
    private PointsAdminService pointsAdminService;

    @BeforeEach
    void setUp() {
        controller = new PointsAdminController();
        pointsAdminService = mock(PointsAdminService.class);

        ReflectionTestUtils.setField(controller, "pointsAdminService", pointsAdminService);
    }

    // ========== getTransactionList ==========

    @Test
    void getTransactionList_shouldReturnPageResult() {
        PageResp<PointsTransactionResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(pointsAdminService.getTransactionList(1, 10, null, null, null, null)).thenReturn(pageResp);

        Result<PageResp<PointsTransactionResp>> result = controller.getTransactionList(1, 10, null, null, null, null);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void getTransactionList_shouldPassFilterParams() {
        PageResp<PointsTransactionResp> pageResp = new PageResp<>(Collections.emptyList(), 5L, 1L, 10L);
        when(pointsAdminService.getTransactionList(1, 10, 1L, "checkin", null, null)).thenReturn(pageResp);

        Result<PageResp<PointsTransactionResp>> result = controller.getTransactionList(1, 10, 1L, "checkin", null, null);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(pointsAdminService).getTransactionList(1, 10, 1L, "checkin", null, null);
    }

    @Test
    void getTransactionList_shouldPropagateException() {
        when(pointsAdminService.getTransactionList(anyInt(), anyInt(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getTransactionList(1, 10, null, null, null, null));
    }

    // ========== getTransactionsByUser ==========

    @Test
    void getTransactionsByUser_shouldReturnPageResult() {
        PageResp<PointsTransaction> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(pointsAdminService.getTransactionsByUserId(1L, 1, 10)).thenReturn(pageResp);

        Result<PageResp<PointsTransaction>> result = controller.getTransactionsByUser(1L, 1, 10);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void getTransactionsByUser_shouldThrowWhenIdInvalid() {
        assertThrows(BusinessException.class, () -> controller.getTransactionsByUser(0L, 1, 10));
    }

    // ========== adjustPoints ==========

    @Test
    void adjustPoints_shouldReturnSuccess() {
        doNothing().when(pointsAdminService).adjustPoints(1L, new BigDecimal("100"), "奖励");

        Map<String, Object> request = new HashMap<>();
        request.put("userId", 1);
        request.put("amount", 100);
        request.put("description", "奖励");
        Result<String> result = controller.adjustPoints(request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("积分调整成功", result.getData());
    }

    @Test
    void adjustPoints_shouldHandleNegativeAmount() {
        doNothing().when(pointsAdminService).adjustPoints(1L, new BigDecimal("-50"), "扣减");

        Map<String, Object> request = new HashMap<>();
        request.put("userId", 1);
        request.put("amount", -50);
        request.put("description", "扣减");
        Result<String> result = controller.adjustPoints(request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void adjustPoints_shouldPropagateException() {
        doThrow(new RuntimeException("insufficient balance")).when(pointsAdminService)
                .adjustPoints(anyLong(), any(), any());

        Map<String, Object> request = new HashMap<>();
        request.put("userId", 1);
        request.put("amount", 100);
        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.adjustPoints(request));
    }

    // ========== getCheckinList ==========

    @Test
    void getCheckinList_shouldReturnPageResult() {
        PageResp<UserCheckinResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(pointsAdminService.getCheckinList(1, 10, null, null, null)).thenReturn(pageResp);

        Result<PageResp<UserCheckinResp>> result = controller.getCheckinList(1, 10, null, null, null);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void getCheckinList_shouldPassFilterParams() {
        PageResp<UserCheckinResp> pageResp = new PageResp<>(Collections.emptyList(), 3L, 1L, 10L);
        when(pointsAdminService.getCheckinList(1, 10, 1L, null, null)).thenReturn(pageResp);

        Result<PageResp<UserCheckinResp>> result = controller.getCheckinList(1, 10, 1L, null, null);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(pointsAdminService).getCheckinList(1, 10, 1L, null, null);
    }

    // ========== getCheckinsByUser ==========

    @Test
    void getCheckinsByUser_shouldReturnPageResult() {
        PageResp<UserCheckin> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(pointsAdminService.getCheckinsByUserId(1L, 1, 10)).thenReturn(pageResp);

        Result<PageResp<UserCheckin>> result = controller.getCheckinsByUser(1L, 1, 10);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void getCheckinsByUser_shouldThrowWhenIdInvalid() {
        assertThrows(BusinessException.class, () -> controller.getCheckinsByUser(0L, 1, 10));
    }

    // ========== getPointsStats ==========

    @Test
    void getPointsStats_shouldReturnStats() {
        Map<String, BigDecimal> stats = new HashMap<>();
        stats.put("totalIssued", new BigDecimal("10000"));
        stats.put("totalConsumed", new BigDecimal("3000"));
        when(pointsAdminService.getPointsStats()).thenReturn(stats);

        Result<Map<String, BigDecimal>> result = controller.getPointsStats();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(new BigDecimal("10000"), result.getData().get("totalIssued"));
    }

    @Test
    void getPointsStats_shouldPropagateException() {
        when(pointsAdminService.getPointsStats()).thenThrow(new RuntimeException("error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getPointsStats());
    }
}
