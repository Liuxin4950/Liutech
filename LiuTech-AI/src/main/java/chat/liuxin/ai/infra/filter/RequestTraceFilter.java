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

/**
 * 请求追踪过滤器：为每个请求分配 traceId 并写入 MDC 与响应头。
 *
 * traceId 优先取请求头 X-Request-Id（网关/前端可传入），否则自动生成 16 位随机 ID。
 * 写入 MDC 后 logback pattern 就能在日志里输出 traceId，跨服务串起请求链路。
 * 同时把 traceId 回写到响应头，方便前端在开发者工具里对上号。
 */
@Slf4j
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    /** 慢请求阈值（毫秒），超过则 warn 级别日志 */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000;
    /** 自动生成的 traceId 长度 */
    private static final int TRACE_ID_LENGTH = 16;
    /** 外部传入 traceId 的最大允许长度 */
    private static final int MAX_TRACE_ID_LENGTH = 32;

    /**
     * 每次请求生成/继承 traceId、写入 MDC 和响应头、记录请求开始与结束日志（含耗时）。
     * finally 中一定要 remove MDC，避免线程复用导致 traceId 串号。
     */
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
            log.debug("===============================请求开始: {} {} [traceId={}]==================================", request.getMethod(), request.getRequestURI(), traceId);
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("慢请求: {} {} [traceId={}] - 用时: {} ms", request.getMethod(), request.getRequestURI(), traceId, cost);
            } else {
                log.debug("请求结束: {} {} [traceId={}] - 用时: {} ms", request.getMethod(), request.getRequestURI(), traceId, cost);
            }
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /** 生成一个 16 位随机 traceId（去掉 UUID 中的短横线并截断） */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }

    /**
     * 清洗外部传入的 traceId：只保留字母数字，最长 32 位，空串则重新生成。
     * 防止上游注入非法字符污染日志或响应头。
     */
    private String sanitize(String id) {
        String normalized = id.replaceAll("[^a-zA-Z0-9]", "");
        if (normalized.length() == 0) {
            return generateTraceId();
        }
        return normalized.length() > MAX_TRACE_ID_LENGTH ? normalized.substring(0, MAX_TRACE_ID_LENGTH) : normalized;
    }
}
