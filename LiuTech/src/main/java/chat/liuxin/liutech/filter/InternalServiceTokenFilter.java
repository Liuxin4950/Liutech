package chat.liuxin.liutech.filter;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 保护 /internal/**，与用户 JWT 鉴权相互独立。 */
@Component
@RequiredArgsConstructor
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-LiuTech-Internal-Token";

    private final ObjectMapper objectMapper;

    @Value("${liutech.internal-token:}")
    private String expectedToken;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER_NAME);
        if (expectedToken == null || expectedToken.isBlank()) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "内部服务令牌未配置");
            return;
        }
        if (!constantTimeEquals(expectedToken, supplied)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "内部服务令牌无效");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (supplied == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorCode errorCode = status == HttpServletResponse.SC_FORBIDDEN
                ? ErrorCode.FORBIDDEN : ErrorCode.SYSTEM_ERROR;
        objectMapper.writeValue(response.getWriter(), Result.fail(errorCode, message));
    }
}
