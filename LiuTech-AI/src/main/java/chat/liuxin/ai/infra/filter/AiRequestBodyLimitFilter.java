package chat.liuxin.ai.infra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** 在反序列化和模型调度前限制推理 JSON 的实际字节数，兼容无 Content-Length 的请求。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AiRequestBodyLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PATHS = Set.of(
            "/ai/chat", "/ai/chat/stream", "/ai/writing", "/ai/writing/stream");
    private final ObjectMapper objectMapper;
    private final int maxBytes;

    public AiRequestBodyLimitFilter(ObjectMapper objectMapper,
            @Value("${spring.ai.security.max-request-body-bytes:1048576}") int maxBytes) {
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("AI 请求体字节上限必须为有效正数");
        }
        this.objectMapper = objectMapper;
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            reject(response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            reject(response);
            return;
        }
        filterChain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("推理 JSON 使用同步请求体读取");
                    }
                };
            }

            @Override
            public BufferedReader getReader() throws IOException {
                String encoding = getCharacterEncoding();
                return new BufferedReader(new InputStreamReader(getInputStream(),
                        encoding == null ? StandardCharsets.UTF_8.name() : encoding));
            }
        }, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "success", false, "code", 413, "message", "请求内容过大，请缩短正文或对话历史"));
    }
}
