package chat.liuxin.ai.infra.filter;

import chat.liuxin.ai.common.client.AuthIntrospectionClient;
import chat.liuxin.ai.common.client.BackendApiTransport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** AI 服务通过主服务身份内省建立 Spring Security 上下文。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteAuthenticationFilter extends OncePerRequestFilter {

    private final AuthIntrospectionClient authClient;
    private final ObjectMapper objectMapper;
    private final SecurityContextRepository securityContextRepository =
            new RequestAttributeSecurityContextRepository();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/ai/models/")
                || path.equals("/ai/runtime")
                || path.startsWith("/ai/tts/audio/")
                || path.startsWith("/ai/internal/")
                || path.equals("/health")
                || path.equals("/actuator/health")
                || path.startsWith("/static/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthIntrospectionClient.AuthenticatedUser user = authClient.introspect(token);
            setAuthentication(user, request, response);
            filterChain.doFilter(request, response);
        } catch (BackendApiTransport.InvalidUserTokenException e) {
            log.debug("AI 身份内省拒绝请求: path={}", request.getRequestURI());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        } catch (BackendApiTransport.AuthenticationAuthorityUnavailableException e) {
            log.warn("AI 身份权威不可用: path={}, error={}", request.getRequestURI(), e.getMessage());
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "身份服务暂时不可用，请稍后重试");
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
    }

    private void setAuthentication(AuthIntrospectionClient.AuthenticatedUser user,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if ("admin".equalsIgnoreCase(user.role())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.username(), null, authorities);
        authentication.setDetails(user.userId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("code", status);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
