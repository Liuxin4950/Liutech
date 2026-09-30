package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.mapper.UserPurgeTaskMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserManagementAiCleanupTest {
    @Test void permanentDeleteEnqueuesCleanupInDatabaseBeforeDeletingUser() {
        UserMapper mapper = mock(UserMapper.class);
        UserPurgeTaskMapper tasks = mock(UserPurgeTaskMapper.class);
        Users user = new Users();
        user.setId(7L);
        user.setUsername("test");
        when(mapper.selectIncludingDeletedForUpdate(7L)).thenReturn(user);
        when(mapper.physicalDeleteById(7L)).thenReturn(1);
        var service = new UserManagementService(mapper, mock(UserUtils.class), mock(BCryptPasswordEncoder.class), tasks);
        assertTrue(service.permanentDeleteUser(7L));
        var order = inOrder(tasks, mapper);
        order.verify(mapper).selectIncludingDeletedForUpdate(7L);
        order.verify(tasks).enqueue(7L);
        order.verify(mapper).physicalDeleteById(7L);
    }

    @Test void absentUserDoesNotEnqueueTask() {
        UserMapper mapper = mock(UserMapper.class);
        UserPurgeTaskMapper tasks = mock(UserPurgeTaskMapper.class);
        var service = new UserManagementService(mapper, mock(UserUtils.class), mock(BCryptPasswordEncoder.class), tasks);
        assertFalse(service.permanentDeleteUser(7L));
        verifyNoInteractions(tasks);
    }
}
