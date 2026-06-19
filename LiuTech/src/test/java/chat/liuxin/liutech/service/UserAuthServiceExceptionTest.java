package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.utils.JwtUtil;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserAuthService 异常路径单元测试
 * 覆盖注册、修改密码、验证码验证等异常分支
 */
class UserAuthServiceExceptionTest {

    private UserAuthService authService;
    private UserMapper userMapper;
    private JwtUtil jwtUtil;
    private UserUtils userUtils;
    private BCryptPasswordEncoder passwordEncoder;
    private VerificationCodeService verificationCodeService;

    @BeforeEach
    void setUp() throws Exception {
        userMapper = mock(UserMapper.class);
        jwtUtil = mock(JwtUtil.class);
        userUtils = mock(UserUtils.class);
        passwordEncoder = new BCryptPasswordEncoder();
        verificationCodeService = mock(VerificationCodeService.class);
        authService = new UserAuthService(userMapper, jwtUtil, userUtils, passwordEncoder, verificationCodeService);

        // 清除暴力破解计数器（通过反射访问静态字段）
        clearBruteForceCounters();
    }

    // ========== register 异常测试 ==========

    @Test
    void register_shouldThrowWhenUsernameExists() {
        Users existingUser = createUser("existinguser", passwordEncoder.encode("pass"));
        when(userMapper.findByUserName("existinguser")).thenReturn(Collections.singletonList(existingUser));

        RegisterReq req = new RegisterReq();
        req.setUsername("existinguser");
        req.setEmail("new@example.com");
        req.setPassword("password123");
        req.setCode("123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void register_shouldThrowWhenEmailExists() {
        when(userMapper.findByUserName("newuser")).thenReturn(Collections.emptyList());
        Users existingEmailUser = createUser("someone", passwordEncoder.encode("pass"));
        when(userMapper.findByEmail("taken@example.com")).thenReturn(Collections.singletonList(existingEmailUser));

        RegisterReq req = new RegisterReq();
        req.setUsername("newuser");
        req.setEmail("taken@example.com");
        req.setPassword("password123");
        req.setCode("123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(ErrorCode.EMAIL_EXISTS.getCode(), ex.getCode());
    }

    // ========== changePassword 异常测试 ==========

    @Test
    void changePassword_shouldThrowWhenOldPasswordWrong() {
        String encodedPassword = passwordEncoder.encode("correctOldPass");
        Users user = createUser("testuser", encodedPassword);
        when(userMapper.selectById(1L)).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(1L, "testuser", encodedPassword, "wrongOldPass", "newPass123"));
        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("原密码错误"));
    }

    @Test
    void changePassword_shouldThrowWhenNewPasswordSameAsOld() {
        String samePassword = "samePass123";
        String encodedPassword = passwordEncoder.encode(samePassword);
        Users user = createUser("testuser", encodedPassword);
        when(userMapper.selectById(1L)).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(1L, "testuser", encodedPassword, samePassword, samePassword));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("新密码不能与原密码相同"));
    }

    @Test
    void changePassword_shouldThrowWhenUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(999L, "ghost", "hash", "old", "new"));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========== 验证码传播异常测试 ==========

    @Test
    void sendVerificationCode_shouldThrowWhenFrequencyTooFast() {
        when(userMapper.findByUserName("user1")).thenReturn(Collections.emptyList());
        when(verificationCodeService.verifyCode(anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.PARAMS_ERROR, "发送过于频繁，请60秒后再试"));

        RegisterReq req = new RegisterReq();
        req.setUsername("user1");
        req.setEmail("user1@example.com");
        req.setPassword("password123");
        req.setCode("123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("发送过于频繁"));
    }

    @Test
    void verifyCode_shouldThrowWhenCodeIncorrect() {
        when(userMapper.findByUserName("user1")).thenReturn(Collections.emptyList());
        when(verificationCodeService.verifyCode("user1@example.com", "REGISTER", "123456"))
                .thenThrow(new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID, "验证码错误"));

        RegisterReq req = new RegisterReq();
        req.setUsername("user1");
        req.setEmail("user1@example.com");
        req.setPassword("password123");
        req.setCode("123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(ErrorCode.VERIFICATION_CODE_INVALID.getCode(), ex.getCode());
        assertEquals("验证码错误", ex.getMessage());
    }

    @Test
    void verifyCode_shouldThrowWhenCodeExpired() {
        when(userMapper.findByUserName("user1")).thenReturn(Collections.emptyList());
        when(verificationCodeService.verifyCode("user1@example.com", "REGISTER", "123456"))
                .thenThrow(new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID, "验证码无效或已过期，请重新获取"));

        RegisterReq req = new RegisterReq();
        req.setUsername("user1");
        req.setEmail("user1@example.com");
        req.setPassword("password123");
        req.setCode("123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(ErrorCode.VERIFICATION_CODE_INVALID.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("已过期"));
    }

    // ========== 辅助方法 ==========

    private Users createUser(String username, String encodedPassword) {
        Users user = new Users();
        user.setId(1L);
        user.setUsername(username);
        user.setPasswordHash(encodedPassword);
        user.setRole("user");
        user.setStatus(1);
        user.setPoints(BigDecimal.ZERO);
        user.setVersion(0);
        user.setCreatedAt(new Date());
        user.setUpdatedAt(new Date());
        return user;
    }

    @SuppressWarnings("unchecked")
    private void clearBruteForceCounters() throws Exception {
        Field failureCountsField = UserAuthService.class.getDeclaredField("LOGIN_FAILURE_COUNTS");
        failureCountsField.setAccessible(true);
        ((ConcurrentHashMap<?, ?>) failureCountsField.get(null)).clear();

        Field lastFailureField = UserAuthService.class.getDeclaredField("LAST_FAILURE_TIMES");
        lastFailureField.setAccessible(true);
        ((ConcurrentHashMap<?, ?>) lastFailureField.get(null)).clear();
    }
}
