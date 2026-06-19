package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.UserCheckinMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.CheckinResp;
import chat.liuxin.liutech.common.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CheckinService 异常路径单元测试
 * 覆盖用户不存在、积分失败等异常分支
 */
@ExtendWith(MockitoExtension.class)
class CheckinServiceExceptionTest {

    @Mock
    private UserCheckinMapper userCheckinMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PointsService pointsService;

    @InjectMocks
    private CheckinService checkinService;

    private static final Long USER_ID = 1L;

    // ========== checkin 异常测试 ==========

    @Test
    void checkin_shouldFailWhenUserNotFound() {
        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(null);
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        Result<CheckinResp> result = checkinService.checkin(USER_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("用户不存在"));
        verify(userCheckinMapper, never()).insert(any());
        verify(pointsService, never()).addPoints(any(), any(), any(), any(), any(), any());
    }

    @Test
    void checkin_shouldFailWhenPointsServiceThrows() {
        Users user = new Users();
        user.setId(USER_ID);
        user.setPoints(BigDecimal.valueOf(50));
        user.setVersion(0);

        when(userCheckinMapper.findByUserIdAndDate(eq(USER_ID), any(LocalDate.class))).thenReturn(null);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userCheckinMapper.findRecentCheckins(USER_ID, 100)).thenReturn(java.util.Collections.emptyList());
        doThrow(new RuntimeException("积分服务异常")).when(pointsService)
                .addPoints(any(), any(), any(), any(), isNull(), any());

        Result<CheckinResp> result = checkinService.checkin(USER_ID);

        assertFalse(result.isSuccess());
        // insert 被调用了，但积分失败导致回滚，返回失败结果
        verify(userCheckinMapper).insert(any());
    }
}
