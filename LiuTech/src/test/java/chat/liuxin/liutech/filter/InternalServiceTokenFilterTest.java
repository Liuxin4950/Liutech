package chat.liuxin.liutech.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InternalServiceTokenFilterTest {

    private InternalServiceTokenFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalServiceTokenFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "expectedToken", "secret");
    }

    @Test
    void validInternalTokenShouldContinue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/auth/introspect");
        request.addHeader(InternalServiceTokenFilter.HEADER_NAME, "secret");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void invalidInternalTokenShouldReturn403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/auth/introspect");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void auditReportRequiresTheSameInternalToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/audit/operations");
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        MockFilterChain blocked = new MockFilterChain();
        filter.doFilter(request, rejected, blocked);
        assertEquals(403, rejected.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(blocked.getRequest());

        MockHttpServletRequest valid = new MockHttpServletRequest("POST", "/internal/audit/operations");
        valid.addHeader(InternalServiceTokenFilter.HEADER_NAME, "secret");
        MockFilterChain accepted = new MockFilterChain();
        filter.doFilter(valid, new MockHttpServletResponse(), accepted);
        assertNotNull(accepted.getRequest());
    }
}
