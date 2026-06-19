package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.resp.LoginResp;
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
 * UserAuthService 单元测试
 * 覆盖登录暴力破解防护、密码验证等核心安全逻辑
 */
class UserAuthServiceTest {

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

    // ========== 登录测试 ==========

    @Test
    void login_shouldSucceedWithValidCredentials() {
        String rawPassword = "correctPassword";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        Users user = createUser("testuser", encodedPassword);
        when(userMapper.findByUserName("testuser")).thenReturn(Collections.singletonList(user));
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString(), anyString())).thenReturn("jwt-token");

        LoginReq req = new LoginReq();
        req.setUsername("testuser");
        req.setPassword(rawPassword);

        LoginResp resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("jwt-token", resp.getToken());
    }

    @Test
    void login_shouldThrowWhenUserNotFound() {
        when(userMapper.findByUserName("nonexistent")).thenReturn(Collections.emptyList());

        LoginReq req = new LoginReq();
        req.setUsername("nonexistent");
        req.setPassword("any");

        assertThrows(BusinessException.class, () -> authService.login(req));
    }

    @Test
    void login_shouldThrowWhenPasswordWrong() {
        String encodedPassword = passwordEncoder.encode("correctPassword");
        Users user = createUser("testuser", encodedPassword);
        when(userMapper.findByUserName("testuser")).thenReturn(Collections.singletonList(user));

        LoginReq req = new LoginReq();
        req.setUsername("testuser");
        req.setPassword("wrongPassword");

        assertThrows(BusinessException.class, () -> authService.login(req));
    }

    @Test
    void login_shouldThrowWhenAccountDisabled() {
        Users user = createUser("testuser", passwordEncoder.encode("pass"));
        user.setStatus(0); // 禁用
        when(userMapper.findByUserName("testuser")).thenReturn(Collections.singletonList(user));

        LoginReq req = new LoginReq();
        req.setUsername("testuser");
        req.setPassword("pass");

        assertThrows(BusinessException.class, () -> authService.login(req));
    }

    // ========== 暴力破解防护测试 ==========

    @Test
    void login_shouldLockAccountAfterMaxFailures() throws Exception {
        String encodedPassword = passwordEncoder.encode("correct");
        Users user = createUser("victim", encodedPassword);
        when(userMapper.findByUserName("victim")).thenReturn(Collections.singletonList(user));

        LoginReq wrongReq = new LoginReq();
        wrongReq.setUsername("victim");
        wrongReq.setPassword("wrong");

        // 连续失败 5 次
        for (int i = 0; i < 5; i++) {
            assertThrows(BusinessException.class, () -> authService.login(wrongReq));
        }

        // 第 6 次即使密码正确也应被锁定
        LoginReq correctReq = new LoginReq();
        correctReq.setUsername("victim");
        correctReq.setPassword("correct");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(correctReq));
        assertTrue(ex.getMessage().contains("锁定"));
    }

    @Test
    void login_shouldResetFailureCountOnSuccess() {
        String rawPassword = "correct";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        Users user = createUser("user1", encodedPassword);
        when(userMapper.findByUserName("user1")).thenReturn(Collections.singletonList(user));
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString(), anyString())).thenReturn("token");

        LoginReq wrongReq = new LoginReq();
        wrongReq.setUsername("user1");
        wrongReq.setPassword("wrong");

        LoginReq correctReq = new LoginReq();
        correctReq.setUsername("user1");
        correctReq.setPassword(rawPassword);

        // 失败 3 次
        for (int i = 0; i < 3; i++) {
            assertThrows(BusinessException.class, () -> authService.login(wrongReq));
        }

        // 成功登录应重置计数
        assertDoesNotThrow(() -> authService.login(correctReq));

        // 再失败 3 次不应被锁定（计数已重置）
        for (int i = 0; i < 3; i++) {
            assertThrows(BusinessException.class, () -> authService.login(wrongReq));
        }

        // 第 4 次仍不应被锁定
        assertDoesNotThrow(() -> authService.login(correctReq));
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
