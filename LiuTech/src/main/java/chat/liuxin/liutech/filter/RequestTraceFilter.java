package chat.liuxin.liutech.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪过滤器
 * 为每个HTTP请求生成并绑定唯一的traceId到MDC，便于日志检索与链路追踪。
 * 优先从请求头 X-Request-Id 复用（如果前端/网关已生成），否则生成本地UUID。
 */
@Slf4j
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incomingTraceId = request.getHeader("X-Request-Id");
        String traceId = StringUtils.hasText(incomingTraceId)
                ? sanitize(incomingTraceId)
                : generateTraceId();

        // 绑定到MDC，供logback pattern输出
        MDC.put(TRACE_ID_KEY, traceId);
        // 透传到响应头与请求属性，方便上下游使用
        response.setHeader("X-Request-Id", traceId);
        request.setAttribute(TRACE_ID_KEY, traceId);

        long start = System.currentTimeMillis();
        try {
            log.info("请求开始: {} {} [traceId={}]", request.getMethod(), request.getRequestURI(), traceId);
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            log.info("请求结束: {} {} [traceId={}] - 用时: {} ms", request.getMethod(), request.getRequestURI(), traceId, cost);
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String generateTraceId() {
        // 截取16位更便于阅读与检索（与示例风格一致）
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String sanitize(String id) {
        // 仅保留字母数字，避免日志污染
        String normalized = id.replaceAll("[^a-zA-Z0-9]", "");
        if (normalized.length() == 0) {
            return generateTraceId();
        }
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }
}