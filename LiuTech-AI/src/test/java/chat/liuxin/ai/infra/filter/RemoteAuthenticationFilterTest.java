package chat.liuxin.ai.infra.filter;

import chat.liuxin.ai.common.client.AuthIntrospectionClient;
import chat.liuxin.ai.common.client.BackendApiTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteAuthenticationFilterTest {

    private AuthIntrospectionClient authClient;
    private RemoteAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        authClient = mock(AuthIntrospectionClient.class);
        filter = new RemoteAuthenticationFilter(authClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenShouldCreateSecurityContext() throws Exception {
        when(authClient.introspect("valid"))
                .thenReturn(new AuthIntrospectionClient.AuthenticatedUser(7L, "liuxin", "admin"));
        MockHttpServletRequest request = request("/ai/writing", "valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(7L, SecurityContextHolder.getContext().getAuthentication().getDetails());
        assertEquals(2, SecurityContextHolder.getContext().getAuthentication().getAuthorities().size());
    }

    @Test
    void invalidTokenShouldReturn401WithoutContinuing() throws Exception {
        when(authClient.introspect("invalid"))
                .thenThrow(new BackendApiTransport.InvalidUserTokenException("登录状态无效或已过期"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/ai/chat", "invalid"), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals(null, chain.getRequest());
    }

    @Test
    void authorityUnavailableShouldReturn503InsteadOfGuestFallback() throws Exception {
        when(authClient.introspect("valid"))
                .thenThrow(new BackendApiTransport.AuthenticationAuthorityUnavailableException("down", new RuntimeException()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/ai/chat", "valid"), response, new MockFilterChain());

        assertEquals(503, response.getStatus());
    }

    @Test
    void publicRuntimeShouldIgnoreAuthorizationHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/ai/runtime", "stale"), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        verify(authClient, never()).introspect("stale");
    }

    private MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
