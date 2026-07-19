package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.AdminLogs;
import chat.liuxin.liutech.resp.LogResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.LogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LogsControllerTest {

    private LogsController controller;
    private LogService logService;

    @BeforeEach
    void setUp() {
        logService = mock(LogService.class);
        controller = new LogsController(logService);
    }

    // ========== getLogList ==========

    @Test
    void getLogList_shouldReturnPageResult() {
        Page<AdminLogs> logPage = new Page<>(1, 10);
        logPage.setTotal(0);
        logPage.setRecords(Collections.emptyList());
        when(logService.getLogList(1, 10, null, null, null, null, null, null)).thenReturn(logPage);

        Result<PageResp<LogResp>> result = controller.getLogList(1, 10, null, null, null, null, null, null);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0L, result.getData().getTotal());
    }

    @Test
    void getLogList_shouldPassFilterParams() {
        Page<AdminLogs> logPage = new Page<>(1, 10);
        logPage.setTotal(2);
        AdminLogs log = new AdminLogs();
        log.setId(1L);
        log.setOperator("admin");
        log.setAction("create");
        log.setStatus(1);
        logPage.setRecords(List.of(log));
        when(logService.getLogList(1, 10, "admin", "create", "post", "2025-01-01", "2025-12-31", 1))
                .thenReturn(logPage);

        Result<PageResp<LogResp>> result = controller.getLogList(1, 10, "admin", "create", "post", "2025-01-01", "2025-12-31", 1);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().getRecords().size());
        assertEquals("admin", result.getData().getRecords().get(0).getOperator());
    }

    @Test
    void getLogList_shouldConvertStatusToChinese() {
        Page<AdminLogs> logPage = new Page<>(1, 10);
        logPage.setTotal(1);
        AdminLogs log = new AdminLogs();
        log.setId(1L);
        log.setStatus(0);
        logPage.setRecords(List.of(log));
        when(logService.getLogList(anyInt(), anyInt(), any(), any(), any(), any(), any(), any())).thenReturn(logPage);

        Result<PageResp<LogResp>> result = controller.getLogList(1, 10, null, null, null, null, null, null);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("失败", result.getData().getRecords().get(0).getStatus());
    }

    @Test
    void getLogList_shouldPropagateException() {
        when(logService.getLogList(anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getLogList(1, 10, null, null, null, null, null, null));
    }

    // ========== getLogById ==========

    @Test
    void getLogById_shouldReturnLogWhenExists() {
        AdminLogs log = new AdminLogs();
        log.setId(1L);
        log.setOperator("admin");
        log.setAction("create");
        log.setStatus(1);
        when(logService.getById(1L)).thenReturn(log);

        Result<LogResp> result = controller.getLogById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("admin", result.getData().getOperator());
        assertEquals("成功", result.getData().getStatus());
    }

    @Test
    void getLogById_shouldReturnErrorWhenNotFound() {
        when(logService.getById(999L)).thenReturn(null);

        Result<LogResp> result = controller.getLogById(999L);

        assertEquals(404, result.getCode());
    }

    // ========== getActionStats ==========

    @Test
    void getActionStats_shouldReturnStats() {
        Map<String, Object> stat = new HashMap<>();
        stat.put("action", "create");
        stat.put("count", 10L);
        when(logService.countByAction()).thenReturn(List.of(stat));

        Result<List<Map<String, Object>>> result = controller.getActionStats();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("create", result.getData().get(0).get("action"));
    }

    @Test
    void getActionStats_shouldPropagateException() {
        when(logService.countByAction()).thenThrow(new RuntimeException("error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getActionStats());
    }

    // ========== getActionTypes ==========

    @Test
    void getActionTypes_shouldReturnHardcodedList() {
        Result<List<String>> result = controller.getActionTypes();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData().contains("create"));
        assertTrue(result.getData().contains("delete"));
        assertTrue(result.getData().contains("login"));
        assertEquals(18, result.getData().size());
    }

    // ========== getTargetTypes ==========

    @Test
    void getTargetTypes_shouldReturnHardcodedList() {
        Result<List<String>> result = controller.getTargetTypes();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData().contains("post"));
        assertTrue(result.getData().contains("user"));
        assertTrue(result.getData().contains("message"));
        assertEquals(16, result.getData().size());
    }
}
