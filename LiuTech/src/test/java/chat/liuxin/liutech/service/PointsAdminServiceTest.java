package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.PointsTransactionMapper;
import chat.liuxin.liutech.mapper.UserCheckinMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.PointsTransaction;
import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PointsTransactionResp;
import chat.liuxin.liutech.resp.UserCheckinResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import org.mockito.ArgumentCaptor;

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

    @InjectMocks
    private PointsAdminService pointsAdminService;

    private static final Long USER_ID = 1L;

    private Users createDefaultUser() {
        Users user = new Users();
        user.setId(USER_ID);
        user.setPoints(BigDecimal.valueOf(100));
        user.setVersion(0);
        return user;
    }

    // ========== adjustPoints 测试 ==========

    @Test
    void adjustPoints_shouldIncreasePoints() {
        Users user = createDefaultUser(); // version=0
        Users updatedUser = createDefaultUser();
        updatedUser.setPoints(BigDecimal.valueOf(150));
        updatedUser.setVersion(1);
        // adjustPoints calls selectById 3 times: initial check, loop iteration, balance query
        when(userMapper.selectById(USER_ID))
                .thenReturn(user)        // initial check (line 121)
                .thenReturn(user)        // loop iteration (line 136)
                .thenReturn(updatedUser); // balance query (line 159)
        when(userMapper.addPointsWithVersion(eq(USER_ID), eq(BigDecimal.valueOf(50)), eq(0), eq(1))).thenReturn(1);

        pointsAdminService.adjustPoints(USER_ID, BigDecimal.valueOf(50), "奖励");

        verify(userMapper).addPointsWithVersion(eq(USER_ID), eq(BigDecimal.valueOf(50)), eq(0), eq(1));
        ArgumentCaptor<PointsTransaction> txCaptor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionMapper).insert(txCaptor.capture());
        PointsTransaction tx = txCaptor.getValue();
        assertEquals("admin_adjust", tx.getTransactionType());
        assertEquals(0, BigDecimal.valueOf(50).compareTo(tx.getAmount()));
        assertEquals(0, BigDecimal.valueOf(150).compareTo(tx.getBalanceAfter()));
    }

    @Test
    void adjustPoints_shouldDecreasePoints() {
        Users user = createDefaultUser(); // version=0
        Users updatedUser = createDefaultUser();
        updatedUser.setPoints(BigDecimal.valueOf(70));
        updatedUser.setVersion(1);
        when(userMapper.selectById(USER_ID))
                .thenReturn(user)        // initial check
                .thenReturn(user)        // loop iteration
                .thenReturn(updatedUser); // balance query
        when(userMapper.deductPointsWithVersion(eq(USER_ID), eq(BigDecimal.valueOf(30)), eq(0), eq(1))).thenReturn(1);

        pointsAdminService.adjustPoints(USER_ID, BigDecimal.valueOf(-30), "扣减");

        verify(userMapper).deductPointsWithVersion(eq(USER_ID), eq(BigDecimal.valueOf(30)), eq(0), eq(1));
        ArgumentCaptor<PointsTransaction> txCaptor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionMapper).insert(txCaptor.capture());
        assertEquals(0, BigDecimal.valueOf(-30).compareTo(txCaptor.getValue().getAmount()));
    }

    @Test
    void adjustPoints_shouldThrowWhenUserIdIsNull() {
        assertThrows(BusinessException.class,
                () -> pointsAdminService.adjustPoints(null, BigDecimal.TEN, "test"));
    }

    @Test
    void adjustPoints_shouldThrowWhenUserIdIsZero() {
        assertThrows(BusinessException.class,
                () -> pointsAdminService.adjustPoints(0L, BigDecimal.TEN, "test"));
    }

    @Test
    void adjustPoints_shouldThrowWhenAmountIsNull() {
        assertThrows(BusinessException.class,
                () -> pointsAdminService.adjustPoints(USER_ID, null, "test"));
    }

    @Test
    void adjustPoints_shouldThrowWhenAmountIsZero() {
        assertThrows(BusinessException.class,
                () -> pointsAdminService.adjustPoints(USER_ID, BigDecimal.ZERO, "test"));
    }

    @Test
    void adjustPoints_shouldThrowWhenUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> pointsAdminService.adjustPoints(999L, BigDecimal.TEN, "test"));
    }

    @Test
    void adjustPoints_shouldThrowWhenInsufficientPointsForDeduction() {
        Users user = createDefaultUser();
        user.setPoints(BigDecimal.valueOf(10));
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pointsAdminService.adjustPoints(USER_ID, BigDecimal.valueOf(-50), "扣减"));
        assertTrue(ex.getMessage().contains("积分不足"));
    }

    @Test
    void adjustPoints_shouldRetryOnOptimisticLockConflict() {
        Users user = createDefaultUser();
        Users updatedUser = createDefaultUser();
        updatedUser.setPoints(BigDecimal.valueOf(150));
        updatedUser.setVersion(1);
        // initial check, loop iter 1 (fails), loop iter 2 (succeeds), balance query
        when(userMapper.selectById(USER_ID))
                .thenReturn(user)         // initial check
                .thenReturn(user)         // retry 1
                .thenReturn(user)         // retry 2
                .thenReturn(updatedUser); // balance query

        when(userMapper.addPointsWithVersion(eq(USER_ID), any(), eq(0), eq(1)))
                .thenReturn(0)  // first attempt fails
                .thenReturn(1); // second attempt succeeds

        assertDoesNotThrow(() ->
                pointsAdminService.adjustPoints(USER_ID, BigDecimal.valueOf(50), "奖励"));
    }

    @Test
    void adjustPoints_shouldThrowAfterMaxRetriesExhausted() {
        Users user = createDefaultUser();
        // initial check + 3 loop iterations = 4 calls
        when(userMapper.selectById(USER_ID))
                .thenReturn(user)
                .thenReturn(user)
                .thenReturn(user)
                .thenReturn(user);
        when(userMapper.addPointsWithVersion(eq(USER_ID), any(), eq(0), eq(1))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> pointsAdminService.adjustPoints(USER_ID, BigDecimal.valueOf(50), "奖励"));
    }

    @Test
    void adjustPoints_shouldUseDefaultDescriptionWhenNull() {
        Users user = createDefaultUser();
        Users updatedUser = createDefaultUser();
        updatedUser.setPoints(BigDecimal.valueOf(150));
        updatedUser.setVersion(1);
        when(userMapper.selectById(USER_ID))
                .thenReturn(user)         // initial check
                .thenReturn(user)         // loop iteration
                .thenReturn(updatedUser); // balance query
        when(userMapper.addPointsWithVersion(eq(USER_ID), any(), eq(0), eq(1))).thenReturn(1);

        pointsAdminService.adjustPoints(USER_ID, BigDecimal.valueOf(50), null);

        ArgumentCaptor<PointsTransaction> txCaptor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionMapper).insert(txCaptor.capture());
        assertEquals("管理员手动调整积分", txCaptor.getValue().getDescription());
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
        when(pointsTransactionMapper.selectTransactionsForAdmin(eq(0), eq(10), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(records);

        PageResp<PointsTransactionResp> result = pointsAdminService.getTransactionList(1, 10, null, null, null, null);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getCurrent());
    }

    // ========== getTransactionsByUserId 测试 ==========

    @Test
    void getTransactionsByUserId_shouldReturnUserTransactions() {
        when(pointsTransactionMapper.selectCount(any())).thenReturn(0L);
        when(pointsTransactionMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResp<PointsTransaction> result = pointsAdminService.getTransactionsByUserId(USER_ID, 1, 10);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    // ========== getCheckinList 测试 ==========

    @Test
    void getCheckinList_shouldReturnPagedCheckins() {
        when(userCheckinMapper.countCheckinsForAdmin(isNull(), isNull(), isNull())).thenReturn(0L);
        when(userCheckinMapper.selectCheckinsForAdmin(eq(0), eq(10), isNull(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        PageResp<UserCheckinResp> result = pointsAdminService.getCheckinList(1, 10, null, null, null);

        assertEquals(0, result.getTotal());
    }

    // ========== getCheckinsByUserId 测试 ==========

    @Test
    void getCheckinsByUserId_shouldReturnUserCheckins() {
        when(userCheckinMapper.selectCount(any())).thenReturn(0L);
        when(userCheckinMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResp<UserCheckin> result = pointsAdminService.getCheckinsByUserId(USER_ID, 1, 10);

        assertEquals(0, result.getTotal());
    }

    // ========== getPointsStats 测试 ==========

    @Test
    void getPointsStats_shouldReturnStats() {
        when(pointsTransactionMapper.sumPointsByTypes(anyList())).thenReturn(BigDecimal.valueOf(500));
        when(pointsTransactionMapper.sumPointsByType("consumption")).thenReturn(BigDecimal.valueOf(-200));
        when(pointsTransactionMapper.sumTotalUserPoints()).thenReturn(BigDecimal.valueOf(300));

        Map<String, BigDecimal> stats = pointsAdminService.getPointsStats();

        assertEquals(0, BigDecimal.valueOf(500).compareTo(stats.get("totalIssued")));
        assertEquals(0, BigDecimal.valueOf(200).compareTo(stats.get("totalConsumed")));
        assertEquals(0, BigDecimal.valueOf(300).compareTo(stats.get("totalBalance")));
    }

    @Test
    void getPointsStats_shouldHandleNullResults() {
        when(pointsTransactionMapper.sumPointsByTypes(anyList())).thenReturn(null);
        when(pointsTransactionMapper.sumPointsByType("consumption")).thenReturn(null);
        when(pointsTransactionMapper.sumTotalUserPoints()).thenReturn(null);

        Map<String, BigDecimal> stats = pointsAdminService.getPointsStats();

        assertEquals(0, BigDecimal.ZERO.compareTo(stats.get("totalIssued")));
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.get("totalConsumed")));
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.get("totalBalance")));
    }
}
