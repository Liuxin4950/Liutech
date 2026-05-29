package chat.liuxin.ai.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRateLimitInterceptorTest {

    @Test
    void shouldRejectWhenGuestExceedsLimit() throws Exception {
        AiRequestRateLimitProperties properties = new AiRequestRateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setGuestMaxRequests(1);
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(properties, new ObjectMapper());

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
