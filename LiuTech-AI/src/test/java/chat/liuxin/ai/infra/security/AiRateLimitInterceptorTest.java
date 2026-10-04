package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.common.utils.AuthUtils;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRateLimitInterceptorTest {

    @Test
    void untrustedCallerCannotRotateForwardedHeadersToResetQuota() throws Exception {
        AiRequestRateLimitProperties properties = new AiRequestRateLimitProperties();
        properties.setGuestMaxRequests(1);
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(properties, new ObjectMapper(), new AuthUtils());
        MockHttpServletRequest first = request("198.51.100.1", "203.0.113.1", "203.0.113.1");
        assertTrue(interceptor.preHandle(first, new MockHttpServletResponse(), new Object()));
        MockHttpServletRequest spoofed = request("198.51.100.1", "203.0.113.2", "203.0.113.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(spoofed, response, new Object()));
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
    }

    @Test
    void trustedNginxMaySupplyRealIpButNotClientControlledXff() throws Exception {
        AiRequestRateLimitProperties properties = new AiRequestRateLimitProperties();
        properties.setGuestMaxRequests(1);
        properties.setTrustedProxies(List.of("172.20.0.0/24"));
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(properties, new ObjectMapper(), new AuthUtils());
        assertTrue(interceptor.preHandle(request("172.20.0.2", "203.0.113.1", "spoofed-1"), new MockHttpServletResponse(), new Object()));
        assertFalse(interceptor.preHandle(request("172.20.0.2", "203.0.113.1", "spoofed-2"), new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request("172.20.0.2", "203.0.113.2", "spoofed-1"), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void reachingBucketCapacityDoesNotResetExistingQuota() throws Exception {
        AiRequestRateLimitProperties properties = new AiRequestRateLimitProperties();
        properties.setGuestMaxRequests(1);
        properties.setMaxTrackedKeys(1);
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(properties, new ObjectMapper(), new AuthUtils());
        assertTrue(interceptor.preHandle(request("198.51.100.1", null, null), new MockHttpServletResponse(), new Object()));
        assertFalse(interceptor.preHandle(request("198.51.100.2", null, null), new MockHttpServletResponse(), new Object()));
        // 触发清理后，原有身份的配额仍耗尽，不能因为新 key 填满而放水。
        ReflectionTestUtils.setField(interceptor, "lastCleanupMillis", new java.util.concurrent.atomic.AtomicLong(0));
        assertFalse(interceptor.preHandle(request("198.51.100.1", null, null), new MockHttpServletResponse(), new Object()));
    }

    private MockHttpServletRequest request(String remote, String realIp, String xff) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/chat");
        request.setRemoteAddr(remote);
        if (realIp != null) request.addHeader("X-Real-IP", realIp);
        if (xff != null) request.addHeader("X-Forwarded-For", xff);
        return request;
    }

    @Test
    void shouldRejectWhenGuestExceedsLimit() throws Exception {
        AiRequestRateLimitProperties properties = new AiRequestRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setGuestMaxRequests(1);
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(properties, new ObjectMapper(), new AuthUtils());

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/ai/chat");
        first.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(first, firstResponse, new Object()));

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/ai/chat");
        second.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(second, secondResponse, new Object()));
        assertEquals(429, secondResponse.getStatus());
        assertTrue(secondResponse.getContentAsString().contains("RATE_LIMITED"));
    }
}
