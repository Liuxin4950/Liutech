package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.mapper.UserPurgeTaskMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.SQLIntegrityConstraintViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserDeletionFailureTest {
    @Test void jdbcForeignKeyCauseIsRecognizedEvenWhenDriverAddsANestedCause() {
        var mapper = mock(UserMapper.class);
        var utils = mock(UserUtils.class);
        var tasks = mock(UserPurgeTaskMapper.class);
        var service = new UserManagementService(mapper,utils,mock(BCryptPasswordEncoder.class),tasks);
        var user = new Users(); user.setId(1L);user.setUsername("test");
        when(mapper.selectIncludingDeletedForUpdate(1L)).thenReturn(user);
        var sql = new SQLIntegrityConstraintViolationException("linked","23000",1451,new IllegalStateException("driver detail"));
        when(mapper.physicalDeleteById(1L)).thenThrow(new DataIntegrityViolationException("cannot delete",sql));
        var failure = assertThrows(BusinessException.class, () -> service.permanentDeleteUser(1L));
        assertTrue(failure.getMessage().contains("可先禁用账户"));
        verifyNoInteractions(utils);
    }
}
