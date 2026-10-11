package chat.liuxin.ai.controller.admin;

import chat.liuxin.ai.common.client.BackendApiTransport;
import chat.liuxin.ai.infra.aspect.AdminOperationLogAspect;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOperationAuditTest {
    private BackendApiTransport transport;
    private WriteFixture proxy;

    @BeforeEach void setup() {
        transport = mock(BackendApiTransport.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new WriteFixture());
        factory.addAspect(new AdminOperationLogAspect(transport));
        proxy = factory.getProxy();
        authenticate("ROLE_ADMIN");
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }
    private void authenticate(String role) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "remote-admin", null, List.of(new SimpleGrantedAuthority(role)));
        authentication.setDetails(8L);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    private void request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContent("{\"apiKey\":\"private-key\",\"prompt\":\"private-prompt\"}".getBytes());
        request.addHeader("Authorization", "Bearer private-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> report() {
        ArgumentCaptor<Map<String, Object>> report = ArgumentCaptor.forClass(Map.class);
        verify(transport, times(1)).internalPost(eq("/internal/audit/operations"), report.capture());
        return report.getValue();
    }

    @Test void successfulAiWriteUsesAuthenticatedSnapshotAndNeverSendsBodyOrToken() {
        request("PUT", "/ai/admin/models/9");
        proxy.write(() -> { authenticate("ROLE_USER"); return Map.of("success", true); });
        Map<String, Object> report = report();
        assertEquals(8L, report.get("operatorId"));
        assertEquals("remote-admin", report.get("operator"));
        assertEquals("admin", report.get("operatorRole"));
        assertEquals(1, report.get("status"));
        assertFalse(report.toString().contains("private-"));
    }

    @Test void failedAiResultAndThrownErrorRemainFailureWithoutRawError() {
        request("POST", "/ai/admin/community/preview");
        proxy.write(() -> Map.of("success", false, "code", 409, "message", "private-prompt"));
        Map<String, Object> report = report();
        assertEquals(0, report.get("status"));
        assertEquals(409, report.get("failureCode"));
        assertFalse(report.toString().contains("private-prompt"));
    }

    @Test void thrownFailureIsRecordedAndRethrown() {
        request("DELETE", "/ai/admin/models/9");
        RuntimeException failure = new RuntimeException("API_KEY=private-key");
        assertSame(failure, assertThrows(RuntimeException.class, () -> proxy.write(() -> { throw failure; })));
        Map<String, Object> report = report();
        assertEquals(0, report.get("status"));
        assertEquals(500, report.get("failureCode"));
        assertFalse(report.toString().contains("private-key"));
    }

    @Test void explicitFalseIsFailureEvenIfControllerIncorrectlyReturnsCode200() {
        request("PUT", "/ai/admin/models/9");
        proxy.write(() -> Map.of("success", false, "code", 200));
        assertEquals(0, report().get("status"));
    }

    @Test void readAndNonAdminNeverInventAnAdminOperation() {
        request("GET", "/ai/admin/models");
        proxy.write(() -> Map.of());
        authenticate("ROLE_USER");
        request("PUT", "/ai/admin/models/9");
        proxy.write(() -> Map.of());
        verifyNoInteractions(transport);
    }

    @Test void nestedOperationReportsOnceAndTransportFailureDoesNotChangeBusinessResponse() {
        request("POST", "/ai/admin/models");
        when(transport.internalPost(anyString(), any())).thenThrow(new RuntimeException("private-token"));
        Object expected = Map.of("success", true);
        assertSame(expected, proxy.write(() -> { proxy.write(() -> Map.of()); return expected; }));
        assertEquals(1, report().get("status"));
    }

    public static class WriteFixture { public Object write(Supplier<?> action) { return action.get(); } }
}
