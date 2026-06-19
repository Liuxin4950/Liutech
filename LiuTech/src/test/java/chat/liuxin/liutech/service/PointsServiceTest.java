package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.PointsTransactionMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.PointsTransaction;
import chat.liuxin.liutech.model.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PointsService 单元测试
 * 覆盖积分扣减、增加、退款的核心逻辑和边界条件
 */
class PointsServiceTest {

    private PointsService pointsService;
    private UserMapper userMapper;
    private PointsTransactionMapper pointsTransactionMapper;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        pointsTransactionMapper = mock(PointsTransactionMapper.class);
        pointsService = new PointsService(userMapper, pointsTransactionMapper);
    }

    // ========== deductPoints 测试 ==========

    @Test
    void deductPoints_shouldSucceedWhenPointsAreSufficient() {
        Users user = createUser(1L, BigDecimal.valueOf(100), 0);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.deductPointsWithVersion(eq(1L), eq(BigDecimal.valueOf(30)), eq(0), eq(1))).thenReturn(1);

        pointsService.deductPoints(1L, BigDecimal.valueOf(30), "resource_download", 5L, "下载资源");

        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionMapper).insert(captor.capture());
        PointsTransaction tx = captor.getValue();
        assertEquals(1L, tx.getUserId());
        assertEquals(PointsService.TYPE_CONSUMPTION, tx.getTransactionType());
        assertEquals(0, BigDecimal.valueOf(-30).compareTo(tx.getAmount()));
        assertEquals(0, BigDecimal.valueOf(70).compareTo(tx.getBalanceAfter()));
    }

    @Test
    void deductPoints_shouldThrowWhenAmountIsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> pointsService.deductPoints(1L, BigDecimal.ZERO, "test", 1L, "test"));
    }

    @Test
    void deductPoints_shouldThrowWhenAmountIsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> pointsService.deductPoints(1L, BigDecimal.valueOf(-5), "test", 1L, "test"));
    }

    @Test
    void deductPoints_shouldThrowWhenUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> pointsService.deductPoints(999L, BigDecimal.TEN, "test", 1L, "test"));
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void deductPoints_shouldThrowWhenPointsInsufficient() {
        Users user = createUser(1L, BigDecimal.valueOf(5), 0);
        when(userMapper.selectById(1L)).thenReturn(user);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> pointsService.deductPoints(1L, BigDecimal.TEN, "test", 1L, "test"));
        assertTrue(ex.getMessage().contains("积分不足"));
    }

    @Test
    void deductPoints_shouldThrowWhenOptimisticLockFails() {
        Users user = createUser(1L, BigDecimal.valueOf(100), 0);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.deductPointsWithVersion(eq(1L), any(), eq(0), eq(1))).thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> pointsService.deductPoints(1L, BigDecimal.TEN, "test", 1L, "test"));
        assertTrue(ex.getMessage().contains("系统繁忙"));
    }

    @Test
    void deductPoints_shouldHandleNullPointsGracefully() {
        Users user = createUser(1L, null, 0);
        when(userMapper.selectById(1L)).thenReturn(user);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> pointsService.deductPoints(1L, BigDecimal.TEN, "test", 1L, "test"));
        assertTrue(ex.getMessage().contains("积分不足"));
    }

    // ========== addPoints 测试 ==========

    @Test
    void addPoints_shouldSucceedOnFirstAttempt() {
        Users user = createUser(1L, BigDecimal.valueOf(50), 0);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.addPointsWithVersion(eq(1L), eq(BigDecimal.valueOf(20)), eq(0), eq(1))).thenReturn(1);

        pointsService.addPoints(1L, BigDecimal.valueOf(20), PointsService.TYPE_CHECKIN,
                PointsService.SOURCE_SYSTEM_REWARD, null, "签到奖励");

        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionMapper).insert(captor.capture());
        PointsTransaction tx = captor.getValue();
        assertEquals(0, BigDecimal.valueOf(20).compareTo(tx.getAmount()));
        assertEquals(0, BigDecimal.valueOf(70).compareTo(tx.getBalanceAfter()));
    }

    @Test
    void addPoints_shouldThrowWhenAmountIsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> pointsService.addPoints(1L, BigDecimal.ZERO, "test", "test", null, "test"));
    }

    @Test
    void addPoints_shouldThrowWhenUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> pointsService.addPoints(999L, BigDecimal.TEN, "test", "test", null, "test"));
    }

    @Test
    void addPoints_shouldRetryOnOptimisticLockConflict() {
        Users user = createUser(1L, BigDecimal.valueOf(50), 0);
        when(userMapper.selectById(1L)).thenReturn(user);
        // 第一次冲突，第二次成功
        when(userMapper.addPointsWithVersion(eq(1L), any(), eq(0), eq(1))).thenReturn(0);
        when(userMapper.addPointsWithVersion(eq(1L), any(), eq(0), eq(1))).thenReturn(1);

        // 不抛异常即为成功
        assertDoesNotThrow(() ->
                pointsService.addPoints(1L, BigDecimal.TEN, "test", "test", null, "test"));
    }

    // ========== refundPoints 测试 ==========

    @Test
    void refundPoints_shouldDelegateToAddPoints() {
        Users user = createUser(1L, BigDecimal.valueOf(50), 0);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.addPointsWithVersion(eq(1L), any(), eq(0), eq(1))).thenReturn(1);

        pointsService.refundPoints(1L, BigDecimal.TEN, 5L, "退款");

        ArgumentCaptor<PointsTransaction> captor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(pointsTransactionMapper).insert(captor.capture());
        PointsTransaction tx = captor.getValue();
        assertEquals(PointsService.TYPE_REFUND, tx.getTransactionType());
        assertEquals(0, BigDecimal.TEN.compareTo(tx.getAmount()));
    }

    private Users createUser(Long id, BigDecimal points, Integer version) {
        Users user = new Users();
        user.setId(id);
        user.setPoints(points);
        user.setVersion(version);
        return user;
    }
}
