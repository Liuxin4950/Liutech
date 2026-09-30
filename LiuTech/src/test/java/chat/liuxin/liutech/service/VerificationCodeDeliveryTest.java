package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.VerificationCodeMapper;
import chat.liuxin.liutech.model.VerificationCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VerificationCodeDeliveryTest {
    @Test void failedEmailRemovesOnlyItsNewCodeAndPreservesTheDomainFailure() {
        var mapper = mock(VerificationCodeMapper.class);
        var email = mock(EmailService.class);
        var service = new VerificationCodeService(mapper, email);
        when(mapper.insert(any(VerificationCode.class))).thenAnswer(call -> {
            call.<VerificationCode>getArgument(0).setId(99L); return 1;
        });
        var failure = new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        doThrow(failure).when(email).sendVerificationCode(anyString(), anyString(), anyString());
        assertSame(failure, assertThrows(BusinessException.class, () -> service.sendCode("test@example.local", "REGISTER", "test")));
        verify(mapper).deleteById(99L);
        verify(mapper, never()).cleanExpired(any());
    }
}
