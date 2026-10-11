package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.AdminLogsMapper;
import chat.liuxin.liutech.model.AdminLogs;
import chat.liuxin.liutech.req.AuditOperationReq;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogAuditIngestionTest {
    private final LogService service = spy(new LogService(mock(AdminLogsMapper.class)));
    private AuditOperationReq request(String role, String path, String method, int status) {
        return new AuditOperationReq(19L, "historic-admin", role, path, method, status, 403, "127.0.0.1", "browser");
    }

    @Test void aiMutationReusesStoreAndKeepsRemoteIdentitySnapshot() {
        doReturn(true).when(service).saveLog(any());
        service.acceptAiOperation(request("admin", "/ai/admin/models/17", "PUT", 1));
        ArgumentCaptor<AdminLogs> saved = ArgumentCaptor.forClass(AdminLogs.class);
        verify(service).saveLog(saved.capture());
        assertEquals(19L, saved.getValue().getOperatorId());
        assertEquals("historic-admin", saved.getValue().getOperator());
        assertEquals("ai_model", saved.getValue().getTargetType());
        assertEquals("17", saved.getValue().getTargetName());
        assertEquals("更新AI 模型", saved.getValue().getDescription());
    }

    @Test void nonAdminReadAndNonManagementRoutesAreRejected() {
        assertThrows(BusinessException.class, () -> service.acceptAiOperation(request("user", "/ai/admin/models/1", "PUT", 1)));
        assertThrows(BusinessException.class, () -> service.acceptAiOperation(request("admin", "/ai/admin/models/1", "GET", 1)));
        assertThrows(BusinessException.class, () -> service.acceptAiOperation(request("admin", "/ai/chat", "POST", 1)));
        verify(service, never()).saveLog(any());
    }

    @Test void remoteFailureAndStorageFailureAreNotAcknowledgedAsSuccess() {
        doReturn(true).when(service).saveLog(any());
        service.acceptAiOperation(request("admin", "/ai/admin/community/tasks/12345678-1234-1234-1234-123456789012/retry", "POST", 0));
        ArgumentCaptor<AdminLogs> saved = ArgumentCaptor.forClass(AdminLogs.class);
        verify(service).saveLog(saved.capture());
        assertEquals(0, saved.getValue().getStatus());
        assertEquals("retry", saved.getValue().getAction());
        assertEquals("AI 操作未完成（错误码 403）", saved.getValue().getErrorMessage());
        doReturn(false).when(service).saveLog(any());
        assertThrows(BusinessException.class, () -> service.acceptAiOperation(request("admin", "/ai/admin/models/17", "DELETE", 1)));
    }
}
