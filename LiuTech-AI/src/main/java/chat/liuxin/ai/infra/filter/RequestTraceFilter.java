package chat.liuxin.ai.infra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String incomingTraceId = request.getHeader("X-Request-Id");
        String traceId = StringUtils.hasText(incomingTraceId)
                ? sanitize(incomingTraceId)
                : generateTraceId();

        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader("X-Request-Id", traceId);
        request.setAttribute(TRACE_ID_KEY, traceId);

        long start = System.currentTimeMillis();
        try {
            log.info("===============================请求开始: {} {} [traceId={}]==================================", request.getMethod(), request.getRequestURI(), traceId);
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            log.info("==========================请求结束: {} {} [traceId={}] - 用时: {} ms==========================", request.getMethod(), request.getRequestURI(), traceId, cost);
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String sanitize(String id) {
        String normalized = id.replaceAll("[^a-zA-Z0-9]", "");
        if (normalized.length() == 0) {
            return generateTraceId();
        }
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }
}
