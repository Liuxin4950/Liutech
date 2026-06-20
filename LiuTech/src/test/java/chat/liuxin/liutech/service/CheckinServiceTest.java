package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.UserCheckinMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.resp.CheckinResp;
import chat.liuxin.liutech.resp.CheckinStatusResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CheckinService 单元测试
 * 覆盖签到、连续天数计算、积分奖励规则等核心逻辑
 */
@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    @Mock
    private UserCheckinMapper userCheckinMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PointsService pointsService;

    @InjectMocks
    private CheckinService checkinService;

    private static final Long USER_ID = 1L;

    private Users createDefaultUser() {
        Users user = new Users();
        user.setId(USER_ID);
        user.setPoints(BigDecimal.valueOf(50));
        user.setVersion(0);
        return user;
    }

    // ========== checkin 测试 ==========

    @Test
    void checkin_shouldSucceedForFirstTimeCheckin() {
        Users user = createDefaultUser();
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(null);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userCheckinMapper.findRecentCheckins(USER_ID, 100)).thenReturn(Collections.emptyList());
        // addPoints mock - doNothing
        doNothing().when(pointsService).addPoints(eq(USER_ID), any(BigDecimal.class), anyString(), anyString(), isNull(), anyString());
        // After addPoints, selectById returns updated user
        Users updatedUser = createDefaultUser();
        updatedUser.setPoints(BigDecimal.valueOf(51));
        when(userMapper.selectById(USER_ID)).thenReturn(updatedUser);

        CheckinResp resp = checkinService.checkin(USER_ID);

        assertEquals(0, BigDecimal.ONE.compareTo(resp.getPointsEarned()));
        assertEquals(1, resp.getConsecutiveDays());
        assertEquals(LocalDate.now(), resp.getCheckinDate());

        verify(userCheckinMapper).insert(any(UserCheckin.class));
        verify(pointsService).addPoints(eq(USER_ID), eq(BigDecimal.ONE), eq(PointsService.TYPE_CHECKIN),
                eq(PointsService.SOURCE_SYSTEM_REWARD), isNull(), contains("连续签到"));
    }

    @Test
    void checkin_shouldFailWhenAlreadyCheckedIn() {
        UserCheckin existingCheckin = new UserCheckin();
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(existingCheckin);

        BusinessException ex = assertThrows(BusinessException.class, () -> checkinService.checkin(USER_ID));
        assertTrue(ex.getMessage().contains("已签到"));
        verify(pointsService, never()).addPoints(any(), any(), any(), any(), any(), any());
    }

    @Test
    void checkin_shouldCalculateConsecutiveDays7Bonus() {
        Users user = createDefaultUser();
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(null);
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        // Simulate 6 consecutive days before today (yesterday through 6 days ago)
        List<UserCheckin> recentCheckins = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            UserCheckin c = new UserCheckin();
            c.setCheckinDate(LocalDate.now().minusDays(i));
            c.setConsecutiveDays(i);
            recentCheckins.add(c);
        }
        when(userCheckinMapper.findRecentCheckins(USER_ID, 100)).thenReturn(recentCheckins);
        doNothing().when(pointsService).addPoints(any(), any(), any(), any(), isNull(), any());
        Users updatedUser = createDefaultUser();
        updatedUser.setPoints(BigDecimal.valueOf(52));
        when(userMapper.selectById(USER_ID)).thenReturn(updatedUser);

        CheckinResp resp = checkinService.checkin(USER_ID);

        // 7 consecutive days = base 1 + bonus 1 = 2
        assertEquals(0, BigDecimal.valueOf(2).compareTo(resp.getPointsEarned()));
        assertEquals(7, resp.getConsecutiveDays());
    }

    @Test
    void checkin_shouldCalculateConsecutiveDays30Bonus() {
        Users user = createDefaultUser();
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(null);
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        // Simulate 29 consecutive days before today
        List<UserCheckin> recentCheckins = new ArrayList<>();
        for (int i = 1; i <= 29; i++) {
            UserCheckin c = new UserCheckin();
            c.setCheckinDate(LocalDate.now().minusDays(i));
            c.setConsecutiveDays(i);
            recentCheckins.add(c);
        }
        when(userCheckinMapper.findRecentCheckins(USER_ID, 100)).thenReturn(recentCheckins);
        doNothing().when(pointsService).addPoints(any(), any(), any(), any(), isNull(), any());
        Users updatedUser = createDefaultUser();
        updatedUser.setPoints(BigDecimal.valueOf(56));
        when(userMapper.selectById(USER_ID)).thenReturn(updatedUser);

        CheckinResp resp = checkinService.checkin(USER_ID);

        // 30 consecutive days = base 1 + bonus 5 = 6
        assertEquals(0, BigDecimal.valueOf(6).compareTo(resp.getPointsEarned()));
        assertEquals(30, resp.getConsecutiveDays());
    }

    @Test
    void checkin_shouldResetConsecutiveDaysOnGap() {
        Users user = createDefaultUser();
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(null);
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        // Simulate a gap: last checkin was 3 days ago (missed 2 days)
        List<UserCheckin> recentCheckins = new ArrayList<>();
        UserCheckin c = new UserCheckin();
        c.setCheckinDate(LocalDate.now().minusDays(3));
        c.setConsecutiveDays(10);
        recentCheckins.add(c);
        when(userCheckinMapper.findRecentCheckins(USER_ID, 100)).thenReturn(recentCheckins);
        doNothing().when(pointsService).addPoints(any(), any(), any(), any(), isNull(), any());
        Users updatedUser = createDefaultUser();
        updatedUser.setPoints(BigDecimal.valueOf(51));
        when(userMapper.selectById(USER_ID)).thenReturn(updatedUser);

        CheckinResp resp = checkinService.checkin(USER_ID);

        // Gap detected, reset to 1
        assertEquals(1, resp.getConsecutiveDays());
        assertEquals(0, BigDecimal.ONE.compareTo(resp.getPointsEarned()));
    }

    @Test
    void checkin_shouldRollbackWhenPointsServiceFail() {
        Users user = createDefaultUser();
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(null);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userCheckinMapper.findRecentCheckins(USER_ID, 100)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("积分服务异常")).when(pointsService)
                .addPoints(any(), any(), any(), any(), isNull(), any());

        // The method should propagate the exception (wrapped in RuntimeException) which triggers rollback
        // The service now throws BusinessException, which triggers rollback
        assertThrows(BusinessException.class, () -> checkinService.checkin(USER_ID));
    }

    // ========== getCheckinStatus 测试 ==========

    @Test
    void getCheckinStatus_shouldReturnCheckedInToday() {
        UserCheckin todayCheckin = new UserCheckin();
        todayCheckin.setCheckinDate(LocalDate.now());
        todayCheckin.setConsecutiveDays(5);
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(todayCheckin);
        when(userCheckinMapper.findLastCheckinByUserId(USER_ID)).thenReturn(todayCheckin);
        when(userCheckinMapper.countByUserId(USER_ID)).thenReturn(10);

        CheckinStatusResp resp = checkinService.getCheckinStatus(USER_ID);

        assertTrue(resp.getHasCheckedInToday());
        assertEquals(5, resp.getConsecutiveDays());
        assertEquals(10, resp.getTotalCheckins());
    }

    @Test
    void getCheckinStatus_shouldReturnNotCheckedInToday() {
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(null);
        when(userCheckinMapper.findLastCheckinByUserId(USER_ID)).thenReturn(null);
        when(userCheckinMapper.countByUserId(USER_ID)).thenReturn(0);

        CheckinStatusResp resp = checkinService.getCheckinStatus(USER_ID);

        assertFalse(resp.getHasCheckedInToday());
        assertEquals(0, resp.getConsecutiveDays());
    }

    @Test
    void getCheckinStatus_shouldHandleNullCountGracefully() {
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(null);
        when(userCheckinMapper.findLastCheckinByUserId(USER_ID)).thenReturn(null);
        when(userCheckinMapper.countByUserId(USER_ID)).thenReturn(null);

        CheckinStatusResp resp = checkinService.getCheckinStatus(USER_ID);

        assertEquals(0, resp.getTotalCheckins());
    }
}
