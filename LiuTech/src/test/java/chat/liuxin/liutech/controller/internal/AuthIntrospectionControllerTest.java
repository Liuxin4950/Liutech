package chat.liuxin.liutech.controller.internal;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.AuthIntrospectionResp;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthIntrospectionControllerTest {

    @Test
    void shouldExposeOnlyCurrentMinimalIdentity() {
        UserUtils userUtils = mock(UserUtils.class);
        Users user = new Users();
        user.setId(8L);
        user.setUsername("liuxin");
        user.setRole("admin");
        when(userUtils.getCurrentUser()).thenReturn(user);

        Result<AuthIntrospectionResp> result = new AuthIntrospectionController(userUtils).introspect();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(8L, result.getData().getUserId());
        assertEquals("liuxin", result.getData().getUsername());
        assertEquals("admin", result.getData().getRole());
    }

    @Test
    void shouldRejectMissingSecurityContextUser() {
        UserUtils userUtils = mock(UserUtils.class);
        when(userUtils.getCurrentUser()).thenReturn(null);

        Result<AuthIntrospectionResp> result = new AuthIntrospectionController(userUtils).introspect();

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
    }
}
