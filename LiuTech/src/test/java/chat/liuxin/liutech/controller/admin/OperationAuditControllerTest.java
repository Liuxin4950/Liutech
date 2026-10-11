package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.aspect.OperationLogAspect;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.AdminLogs;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.LoginResp;
import chat.liuxin.liutech.resp.UserResp;
import chat.liuxin.liutech.service.LogService;
import chat.liuxin.liutech.utils.JwtUtil;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 通过真正 Spring AOP 代理验证缺少注解的 Controller 仍被统一覆盖。 */
class OperationAuditControllerTest {
    private LogService logs;
    private UserUtils users;
    private JwtUtil jwt;
    private WriteFixture proxy;

    @BeforeEach void setup() {
        logs = mock(LogService.class);
        when(logs.saveLog(any())).thenReturn(true);
        users = mock(UserUtils.class);
        jwt = mock(JwtUtil.class);
        when(users.getCurrentUserId()).thenReturn(7L);
        when(users.getCurrentUsername()).thenReturn("original");
        AspectJProxyFactory factory = new AspectJProxyFactory(new WriteFixture());
        factory.addAspect(new OperationLogAspect(logs, users, jwt));
        proxy = factory.getProxy();
    }

    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    private void request(String method, String path) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(method, path)));
    }

    private AdminLogs saved() {
        ArgumentCaptor<AdminLogs> captor = ArgumentCaptor.forClass(AdminLogs.class);
        verify(logs, times(1)).saveLog(captor.capture());
        return captor.getValue();
    }

    @Test void unannotatedWriteUsesExistingAuditStore() {
        request("PUT", "/admin/settings");
        proxy.write(() -> Result.success());
        AdminLogs entry = saved();
        assertEquals("update", entry.getAction());
        assertEquals("system_setting", entry.getTargetType());
        assertEquals(7L, entry.getOperatorId());
        assertEquals(1, entry.getStatus());
    }

    @Test void readsAndViewCounterDoNotBecomeUserOperations() {
        request("GET", "/admin/settings");
        proxy.write(() -> Result.success());
        request("POST", "/posts/17/view");
        proxy.write(() -> Result.success());
        verifyNoInteractions(logs);
    }

    @Test void returnedBusinessFailureIsNotSuccessAndErrorPayloadIsNotStored() {
        request("DELETE", "/admin/users/7");
        Result<?> result = (Result<?>) proxy.write(() -> Result.fail(409, "password=secret token=hidden"));
        assertEquals(409, result.getCode());
        AdminLogs entry = saved();
        assertEquals(0, entry.getStatus());
        assertEquals("操作未完成（错误码 409）", entry.getErrorMessage());
    }

    @Test void thrownBusinessFailureIsRethrownAndRecordedSafely() {
        request("POST", "/resource/purchase/12");
        BusinessException failure = new BusinessException(ErrorCode.PARAMS_ERROR, "prompt=private SQL=secret");
        assertSame(failure, assertThrows(BusinessException.class, () -> proxy.write(() -> { throw failure; })));
        AdminLogs entry = saved();
        assertEquals("purchase", entry.getAction());
        assertEquals(0, entry.getStatus());
        assertFalse(entry.getErrorMessage().contains("private"));
    }

    @Test void deniedControllerWriteIsRecordedAsFailure() {
        request("PUT", "/admin/settings");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> proxy.write(() -> { throw new org.springframework.security.access.AccessDeniedException("private details"); }));
        assertEquals("操作未完成（错误码 403）", saved().getErrorMessage());
    }

    @Test void deletionAndRenameKeepPreExecutionIdentity() {
        request("DELETE", "/admin/users/7/permanent");
        proxy.write(() -> {
            when(users.getCurrentUserId()).thenReturn(null);
            when(users.getCurrentUsername()).thenReturn("renamed");
            return Result.success();
        });
        AdminLogs entry = saved();
        assertEquals("original", entry.getOperator());
        assertEquals(7L, entry.getOperatorId());
    }

    @Test void nestedAnnotatedOperationIsRecordedOnlyOnceWithFinalOutcome() {
        request("POST", "/admin/users");
        proxy.write(() -> {
            proxy.annotated(() -> Result.success());
            return Result.fail(400, "outer failure");
        });
        AdminLogs entry = saved();
        assertEquals(0, entry.getStatus());
        assertEquals("create", entry.getAction());
    }

    @Test void annotationAndMixedSpelPreserveReadableBusinessMetadata() {
        request("POST", "/admin/users");
        Users user = new Users();
        user.setUsername("new-user");
        proxy.createUser(user);
        AdminLogs entry = saved();
        assertEquals("new-user", entry.getTargetName());
        assertEquals("创建用户: new-user", entry.getDescription());
    }

    @Test void successfulLoginUsesExistingSignedIdentityWithoutSavingToken() {
        request("POST", "/user/login");
        when(users.getCurrentUsername()).thenReturn(null);
        when(users.getCurrentUserId()).thenReturn(null);
        when(jwt.parseIdentity("private-jwt")).thenReturn(new JwtUtil.TokenIdentity(23L, "login-user", "digest"));
        proxy.write(() -> Result.success(new LoginResp("private-jwt")));
        AdminLogs entry = saved();
        assertEquals("login", entry.getAction());
        assertEquals("login-user", entry.getOperator());
        assertEquals(23L, entry.getOperatorId());
        assertFalse(entry.toString().contains("private-jwt"));
    }

    @Test void failedAnonymousLoginCannotBeAttributedToAnAdministrator() {
        request("POST", "/user/login");
        when(users.getCurrentUsername()).thenReturn(null);
        when(users.getCurrentUserId()).thenReturn(null);
        proxy.write(() -> Result.fail(401, "invalid credentials"));
        AdminLogs entry = saved();
        assertEquals("未认证访问", entry.getOperator());
        assertNull(entry.getOperatorId());
        assertEquals(0, entry.getStatus());
        verifyNoInteractions(jwt);
    }

    @Test void registrationUsesReturnedNewAccountIdentity() {
        request("POST", "/user/register");
        when(users.getCurrentUsername()).thenReturn(null);
        when(users.getCurrentUserId()).thenReturn(null);
        UserResp user = new UserResp();
        user.setId(30L);
        user.setUsername("new-user");
        proxy.write(() -> Result.success(user));
        assertEquals(30L, saved().getOperatorId());
    }

    public static class WriteFixture {
        public Object write(Supplier<?> action) { return action.get(); }
        @OperationLog(action = "update", targetType = "user", description = "更新用户")
        public Object annotated(Supplier<?> action) { return action.get(); }
        @OperationLog(action = "create", targetType = "user", description = "创建用户: #user.username", targetName = "#user.username")
        public Result<?> createUser(Users user) { return Result.success(); }
    }
}
