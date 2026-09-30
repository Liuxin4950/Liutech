package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.PointsTransactionMapper;
import chat.liuxin.liutech.mapper.UserCheckinMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.PointsTransaction;
import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PointsTransactionResp;
import chat.liuxin.liutech.resp.UserCheckinResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PointsAdminService 单元测试
 * 覆盖管理员积分调整、流水查询、签到记录查询、统计等核心逻辑
 */
@ExtendWith(MockitoExtension.class)
class PointsAdminServiceTest {

    @Mock
    private PointsTransactionMapper pointsTransactionMapper;

    @Mock
    private UserCheckinMapper userCheckinMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PointsService pointsService;

    @InjectMocks
    private PointsAdminService pointsAdminService;

    private static final Long USER_ID = 1L;

    // ========== adjustPoints 测试 ==========

    @Test void adjustPoints_shouldDelegatePositiveAndNegativeAmountsWithAdminType() {
        pointsAdminService.adjustPoints(USER_ID, BigDecimal.TEN, "奖励");
        verify(pointsService).addPoints(USER_ID, BigDecimal.TEN, PointsService.TYPE_ADMIN_ADJUST,
                PointsService.SOURCE_ADMIN_MANUAL, null, "奖励");
        pointsAdminService.adjustPoints(USER_ID, BigDecimal.TEN.negate(), null);
        verify(pointsService).deductPoints(USER_ID, BigDecimal.TEN, PointsService.TYPE_ADMIN_ADJUST,
                PointsService.SOURCE_ADMIN_MANUAL, null, "管理员手动调整积分");
        verifyNoInteractions(userMapper);
        verify(pointsTransactionMapper, never()).insert(any(PointsTransaction.class));
    }

    @Test void adjustPoints_shouldRejectInvalidParameters() {
        assertThrows(BusinessException.class, () -> pointsAdminService.adjustPoints(null, BigDecimal.TEN, "test"));
        assertThrows(BusinessException.class, () -> pointsAdminService.adjustPoints(0L, BigDecimal.TEN, "test"));
        assertThrows(BusinessException.class, () -> pointsAdminService.adjustPoints(USER_ID, null, "test"));
        assertThrows(BusinessException.class, () -> pointsAdminService.adjustPoints(USER_ID, BigDecimal.ZERO, "test"));
        verifyNoInteractions(pointsService);
    }

    @Test void adjustPoints_shouldPropagateDomainFailuresWithoutRetrying() {
        doThrow(new BusinessException(chat.liuxin.liutech.common.ErrorCode.PARAMS_ERROR, "积分不足"))
                .when(pointsService).deductPoints(USER_ID, BigDecimal.TEN, PointsService.TYPE_ADMIN_ADJUST,
                        PointsService.SOURCE_ADMIN_MANUAL, null, "扣减");
        assertThrows(BusinessException.class, () -> pointsAdminService.adjustPoints(USER_ID, BigDecimal.TEN.negate(), "扣减"));
        verify(pointsService, times(1)).deductPoints(USER_ID, BigDecimal.TEN, PointsService.TYPE_ADMIN_ADJUST,
                PointsService.SOURCE_ADMIN_MANUAL, null, "扣减");
    }

    // ========== getTransactionList 测试 ==========

    @Test
    void getTransactionList_shouldReturnPagedResults() {
        List<PointsTransactionResp> records = new ArrayList<>();
        PointsTransactionResp resp = new PointsTransactionResp();
        resp.setId(1L);
        resp.setUserId(USER_ID);
        resp.setAmount(BigDecimal.TEN);
        records.add(resp);

        when(pointsTransactionMapper.countTransactionsForAdmin(isNull(), isNull(), isNull(), isNull())).thenReturn(1L);
        when(pointsTransactionMapper.selectTransactionsForAdmin(eq(0L), eq(10), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(records);

        PageResp<PointsTransactionResp> result = pointsAdminService.getTransactionList(1, 10, null, null, null, null);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getCurrent());
    }

    // ========== getTransactionsByUserId 测试 ==========

    @Test
    void getTransactionsByUserId_shouldReturnUserTransactions() {
        when(pointsTransactionMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        PageResp<PointsTransaction> result = pointsAdminService.getTransactionsByUserId(USER_ID, 1, 10);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    // ========== getCheckinList 测试 ==========

    @Test
    void getCheckinList_shouldReturnPagedCheckins() {
        when(userCheckinMapper.countCheckinsForAdmin(isNull(), isNull(), isNull())).thenReturn(0L);
        when(userCheckinMapper.selectCheckinsForAdmin(eq(0L), eq(10), isNull(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        PageResp<UserCheckinResp> result = pointsAdminService.getCheckinList(1, 10, null, null, null);

        assertEquals(0, result.getTotal());
    }

    // ========== getCheckinsByUserId 测试 ==========

    @Test
    void getCheckinsByUserId_shouldReturnUserCheckins() {
        when(userCheckinMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        PageResp<UserCheckin> result = pointsAdminService.getCheckinsByUserId(USER_ID, 1, 10);

        assertEquals(0, result.getTotal());
    }

    @Test void getTransactionList_shouldRejectInvalidPagesBeforeQueryAndUseLongOffsets() {
        assertThrows(BusinessException.class, () -> pointsAdminService.getTransactionList(0, 10, null, null, null, null));
        assertThrows(BusinessException.class, () -> pointsAdminService.getTransactionList(1, 501, null, null, null, null));
        verifyNoInteractions(pointsTransactionMapper);
        when(pointsTransactionMapper.countTransactionsForAdmin(isNull(), isNull(), isNull(), isNull())).thenReturn(0L);
        when(pointsTransactionMapper.selectTransactionsForAdmin(anyLong(), eq(500), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());
        var result = pointsAdminService.getTransactionList(Integer.MAX_VALUE, 500, null, null, null, null);
        verify(pointsTransactionMapper).selectTransactionsForAdmin(1073741823000L, 500, null, null, null, null);
        assertEquals(Integer.MAX_VALUE, result.getCurrent());
    }

    // ========== getPointsStats 测试 ==========

    @Test
    void getPointsStats_shouldReturnStats() {
        when(pointsTransactionMapper.sumIssuedPoints()).thenReturn(BigDecimal.valueOf(500));
        when(pointsTransactionMapper.sumConsumedPoints()).thenReturn(BigDecimal.valueOf(200));
        when(pointsTransactionMapper.sumTotalUserPoints()).thenReturn(BigDecimal.valueOf(300));

        Map<String, BigDecimal> stats = pointsAdminService.getPointsStats();

        assertEquals(0, BigDecimal.valueOf(500).compareTo(stats.get("totalIssued")));
        assertEquals(0, BigDecimal.valueOf(200).compareTo(stats.get("totalConsumed")));
        assertEquals(0, BigDecimal.valueOf(300).compareTo(stats.get("totalBalance")));
    }

    @Test
    void getPointsStats_shouldHandleNullResults() {
        when(pointsTransactionMapper.sumIssuedPoints()).thenReturn(null);
        when(pointsTransactionMapper.sumConsumedPoints()).thenReturn(null);
        when(pointsTransactionMapper.sumTotalUserPoints()).thenReturn(null);

        Map<String, BigDecimal> stats = pointsAdminService.getPointsStats();

        assertEquals(0, BigDecimal.ZERO.compareTo(stats.get("totalIssued")));
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.get("totalConsumed")));
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.get("totalBalance")));
    }
}
