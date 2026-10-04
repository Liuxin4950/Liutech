package chat.liuxin.ai.infra.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AiRequestBodyLimitFilterTest {
    private final AiRequestBodyLimitFilter filter = new AiRequestBodyLimitFilter(new ObjectMapper(), 8);

    @Test
    void declaredOversizedBodyIsRejectedBeforeTheController() throws Exception {
        MockHttpServletRequest request = request("/ai/chat/stream", "123456789");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
        assertTrue(response.getContentAsString().contains("请求内容过大"));
    }

    @Test
    void chunkedOversizedBodyCannotBypassTheLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/writing/stream") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.addHeader("Transfer-Encoding", "chunked");
        request.setContent("123456789".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void exactLimitBodyRemainsAvailableForJackson() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/ai/chat", "12345678"), new MockHttpServletResponse(), chain);
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertEquals("12345678", new String(forwarded.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void audioUploadsAreNotSubjectToTheInferenceJsonLimit() throws Exception {
        MockHttpServletRequest request = request("/ai/admin/tts/siliconflow/voice", "123456789");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertSame(request, chain.getRequest());
    }

    private MockHttpServletRequest request(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
