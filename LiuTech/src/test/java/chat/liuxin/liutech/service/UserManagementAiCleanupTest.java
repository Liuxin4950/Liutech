package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserManagementAiCleanupTest {

    @Test
    void permanentDeleteShouldPurgeAiDataBeforeDeletingUser() {
        UserMapper mapper = mock(UserMapper.class);
        UserUtils userUtils = mock(UserUtils.class);
        AiUserDataClient aiClient = mock(AiUserDataClient.class);
        Users user = new Users();
        user.setId(7L);
        user.setUsername("liuxin");
        when(mapper.selectById(7L)).thenReturn(user);
        when(mapper.physicalDeleteById(7L)).thenReturn(1);
        UserManagementService service = new UserManagementService(
                mapper, userUtils, mock(BCryptPasswordEncoder.class), aiClient);

        assertTrue(service.permanentDeleteUser(7L));

        var order = inOrder(aiClient, mapper);
        order.verify(aiClient).purgeUser(7L);
        order.verify(mapper).selectById(7L);
        order.verify(mapper).physicalDeleteById(7L);
    }

    @Test
    void permanentDeleteShouldAbortWhenAiPurgeFails() {
        UserMapper mapper = mock(UserMapper.class);
        AiUserDataClient aiClient = mock(AiUserDataClient.class);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "AI cleanup failed"))
                .when(aiClient).purgeUser(7L);
        UserManagementService service = new UserManagementService(
                mapper, mock(UserUtils.class), mock(BCryptPasswordEncoder.class), aiClient);

        assertThrows(BusinessException.class, () -> service.permanentDeleteUser(7L));
        verify(mapper, never()).physicalDeleteById(7L);
    }
}
