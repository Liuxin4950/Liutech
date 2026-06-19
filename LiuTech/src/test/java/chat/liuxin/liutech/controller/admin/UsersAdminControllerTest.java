package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.UserResp;
import chat.liuxin.liutech.service.UserManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UsersAdminControllerTest {

    private UsersAdminController controller;
    private UserManagementService userManagementService;

    @BeforeEach
    void setUp() {
        controller = new UsersAdminController();
        userManagementService = mock(UserManagementService.class);

        ReflectionTestUtils.setField(controller, "userManagementService", userManagementService);
    }

    // ========== getUserList ==========

    @Test
    void getUserList_shouldReturnPageResult() {
        PageResp<UserResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(userManagementService.getUserListForAdmin(1, 10, null, null, null, null, false)).thenReturn(pageResp);

        Result<PageResp<UserResp>> result = controller.getUserList(1, 10, null, null, null, null, false);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void getUserList_shouldPassFilterParams() {
        PageResp<UserResp> pageResp = new PageResp<>(Collections.emptyList(), 2L, 1L, 10L);
        when(userManagementService.getUserListForAdmin(1, 10, "admin", "admin@test.com", "ADMIN", 1, true))
                .thenReturn(pageResp);

        Result<PageResp<UserResp>> result = controller.getUserList(1, 10, "admin", "admin@test.com", "ADMIN", 1, true);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(userManagementService).getUserListForAdmin(1, 10, "admin", "admin@test.com", "ADMIN", 1, true);
    }

    @Test
    void getUserList_shouldHandleException() {
        when(userManagementService.getUserListForAdmin(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("db error"));

        Result<PageResp<UserResp>> result = controller.getUserList(1, 10, null, null, null, null, false);

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
    }

    // ========== getUserById ==========

    @Test
    void getUserById_shouldReturnUserWhenExists() {
        Users user = new Users();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("secret");
        when(userManagementService.findUserById(1L)).thenReturn(user);

        Result<Users> result = controller.getUserById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("admin", result.getData().getUsername());
        assertNull(result.getData().getPasswordHash());
    }

    @Test
    void getUserById_shouldReturnErrorWhenNotFound() {
        when(userManagementService.findUserById(999L)).thenReturn(null);

        Result<Users> result = controller.getUserById(999L);

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), result.getCode());
    }

    @Test
    void getUserById_shouldThrowWhenIdInvalid() {
        assertThrows(BusinessException.class, () -> controller.getUserById(0L));
    }

    // ========== createUser ==========

    @Test
    void createUser_shouldReturnSuccess() {
        Users user = new Users();
        user.setUsername("newuser");
        user.setEmail("new@test.com");
        when(userManagementService.saveUser(user)).thenReturn(true);

        Result<String> result = controller.createUser(user);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void createUser_shouldReturnErrorWhenServiceFails() {
        Users user = new Users();
        user.setUsername("newuser");
        user.setEmail("new@test.com");
        when(userManagementService.saveUser(user)).thenReturn(false);

        Result<String> result = controller.createUser(user);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    @Test
    void createUser_shouldThrowWhenNull() {
        assertThrows(BusinessException.class, () -> controller.createUser(null));
    }

    // ========== updateUser ==========

    @Test
    void updateUser_shouldReturnSuccess() {
        Users user = new Users();
        user.setEmail("updated@test.com");
        user.setPasswordHash("newpass");
        when(userManagementService.updateUserById(any())).thenReturn(true);

        Result<String> result = controller.updateUser(1L, user);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1L, user.getId());
    }

    @Test
    void updateUser_shouldPreservePasswordWhenEmpty() {
        Users existingUser = new Users();
        existingUser.setPasswordHash("oldHash");
        when(userManagementService.findUserById(1L)).thenReturn(existingUser);
        when(userManagementService.updateUserById(any())).thenReturn(true);

        Users user = new Users();
        user.setEmail("updated@test.com");
        Result<String> result = controller.updateUser(1L, user);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("oldHash", user.getPasswordHash());
    }

    @Test
    void updateUser_shouldReturnErrorWhenServiceFails() {
        Users user = new Users();
        user.setEmail("updated@test.com");
        user.setPasswordHash("pass");
        when(userManagementService.updateUserById(any())).thenReturn(false);

        Result<String> result = controller.updateUser(1L, user);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== deleteUser ==========

    @Test
    void deleteUser_shouldReturnSuccess() {
        when(userManagementService.removeUserById(1L)).thenReturn(true);

        Result<String> result = controller.deleteUser(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void deleteUser_shouldReturnErrorWhenServiceFails() {
        when(userManagementService.removeUserById(1L)).thenReturn(false);

        Result<String> result = controller.deleteUser(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchDeleteUsers ==========

    @Test
    void batchDeleteUsers_shouldReturnSuccess() {
        when(userManagementService.removeUsersByIds(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchDeleteUsers(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchDeleteUsers_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchDeleteUsers(Collections.emptyList()));
    }

    // ========== updateUserStatus ==========

    @Test
    void updateUserStatus_shouldEnableUser() {
        when(userManagementService.updateUserById(any())).thenReturn(true);

        Result<String> result = controller.updateUserStatus(1L, true);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(userManagementService).updateUserById(argThat(u -> u.getStatus() == 1));
    }

    @Test
    void updateUserStatus_shouldDisableUser() {
        when(userManagementService.updateUserById(any())).thenReturn(true);

        Result<String> result = controller.updateUserStatus(1L, false);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(userManagementService).updateUserById(argThat(u -> u.getStatus() == 0));
    }

    @Test
    void updateUserStatus_shouldThrowWhenIdInvalid() {
        assertThrows(BusinessException.class, () -> controller.updateUserStatus(0L, true));
    }

    // ========== batchUpdateUserStatus ==========

    @Test
    void batchUpdateUserStatus_shouldReturnSuccess() {
        when(userManagementService.batchUpdateUserStatus(List.of(1L, 2L), true)).thenReturn(true);

        Result<String> result = controller.batchUpdateUserStatus(List.of(1L, 2L), true);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchUpdateUserStatus_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchUpdateUserStatus(Collections.emptyList(), true));
    }

    // ========== restoreUser ==========

    @Test
    void restoreUser_shouldReturnSuccess() {
        when(userManagementService.restoreUser(1L)).thenReturn(true);

        Result<String> result = controller.restoreUser(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void restoreUser_shouldReturnErrorWhenServiceFails() {
        when(userManagementService.restoreUser(1L)).thenReturn(false);

        Result<String> result = controller.restoreUser(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchRestoreUsers ==========

    @Test
    void batchRestoreUsers_shouldReturnSuccess() {
        when(userManagementService.restoreUsers(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchRestoreUsers(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchRestoreUsers_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchRestoreUsers(Collections.emptyList()));
    }

    // ========== permanentDeleteUser ==========

    @Test
    void permanentDeleteUser_shouldReturnSuccess() {
        when(userManagementService.permanentDeleteUser(1L)).thenReturn(true);

        Result<String> result = controller.permanentDeleteUser(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void permanentDeleteUser_shouldReturnErrorWhenServiceFails() {
        when(userManagementService.permanentDeleteUser(1L)).thenReturn(false);

        Result<String> result = controller.permanentDeleteUser(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchPermanentDeleteUsers ==========

    @Test
    void batchPermanentDeleteUsers_shouldReturnSuccess() {
        when(userManagementService.batchPermanentDeleteUsers(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchPermanentDeleteUsers(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchPermanentDeleteUsers_shouldThrowWhenEmpty() {
        assertThrows(BusinessException.class, () -> controller.batchPermanentDeleteUsers(Collections.emptyList()));
    }
}
