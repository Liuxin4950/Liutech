package chat.liuxin.liutech.filter;

import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.service.UserAuthLookupService;
import chat.liuxin.liutech.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JwtAuthenticationFilterTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void publicDownstreamExceptionDoesNotRunFilterChainTwice() {
        var filter = new JwtAuthenticationFilter();
        var chain = mock(jakarta.servlet.FilterChain.class);
        var request = new MockHttpServletRequest("POST", "/user/login");
        var response = new MockHttpServletResponse();
        try {
            doThrow(new jakarta.servlet.ServletException("downstream failed")).when(chain).doFilter(request, response);
            assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(request, response, chain));
            verify(chain, times(1)).doFilter(request, response);
        } catch (java.io.IOException | jakarta.servlet.ServletException e) { throw new AssertionError(e); }
    }

    @Test void databaseOutageIs503InsteadOfInvalidCredentials() throws Exception {
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.parseIdentity("test")).thenReturn(new JwtUtil.TokenIdentity(7L, "test", "digest"));
        var lookup = mock(UserAuthLookupService.class);
        when(lookup.selectById(7L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        var filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwt);
        ReflectionTestUtils.setField(filter, "userAuthLookupService", lookup);
        ReflectionTestUtils.setField(filter, "objectMapper", new tools.jackson.databind.ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/posts/my");
        request.addHeader("Authorization", "Bearer test");
        var response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("503"));
        verifyNoInteractions(chain);
    }

    @Test void requestParsesTokenOnceAndUsesCurrentDatabaseRoleAndPassword() throws Exception {
        JwtUtil jwt = spy(new JwtUtil());
        ReflectionTestUtils.setField(jwt, "secretKey", "local-test-material-".repeat(5));
        ReflectionTestUtils.setField(jwt, "expirationTime", 60000L);
        UserMapper mapper = mock(UserMapper.class);
        Users user = new Users();
        user.setId(7L); user.setUsername("test"); user.setRole("user");
        user.setStatus(1); user.setPasswordHash("hash");
        when(mapper.selectById(7L)).thenReturn(user);
        var filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwt);
        ReflectionTestUtils.setField(filter, "userAuthLookupService", new UserAuthLookupService(mapper, jwt));
        String token = jwt.generateToken(7L, "test", "admin", "hash");
        var request = new MockHttpServletRequest("GET", "/posts/my");
        request.setServletPath("/posts/my");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        var identity = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(identity);
        assertTrue(identity.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertFalse(identity.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(jwt, times(1)).parseIdentity(token);
        verify(jwt, never()).validateToken(anyString());
        verify(jwt, never()).getPasswordHashFromToken(anyString());
        assertFalse(new UserAuthLookupService(mapper, jwt).isCurrentUserTokenValid(user,
                new JwtUtil.TokenIdentity(7L, "test", "old-password-digest")));
    }
}
