package chat.liuxin.ai.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SecurityCorsConfigurationTest {

    @Test
    void shouldAllowLiuxinChatPort81Origin() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/models/2/default");

        CorsConfiguration configuration = source.getCorsConfiguration(request);
        assertNotNull(configuration);
        assertNotNull(configuration.checkOrigin("http://liuxin.chat:81"));
    }

    @Test
    void shouldRejectUnknownOrigin() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/models/2/default");

        CorsConfiguration configuration = source.getCorsConfiguration(request);
        assertNotNull(configuration);
        assertNull(configuration.checkOrigin("http://evil.example:81"));
    }
}

