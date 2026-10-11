package chat.liuxin.ai.infra.aspect;

import chat.liuxin.ai.common.client.BackendApiTransport;
import chat.liuxin.ai.infra.exception.AIServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** AI 管理写入复用主服务的 system_logs，身份只取远程内省建立的上下文。 */
@Slf4j
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class AdminOperationLogAspect {
    private static final String ACTIVE = AdminOperationLogAspect.class.getName() + ".active";
    private final BackendApiTransport transport;

    @Around("execution(public * chat.liuxin.ai.controller.admin..*(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (request == null || !Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())
                || request.getAttribute(ACTIVE) != null || auth == null || !auth.isAuthenticated()
                || !(auth.getDetails() instanceof Long userId)
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return point.proceed();
        }
        request.setAttribute(ACTIVE, Boolean.TRUE);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("operatorId", userId);
        report.put("operator", auth.getName());
        report.put("operatorRole", "admin");
        report.put("path", request.getRequestURI());
        report.put("method", request.getMethod());
        String forwarded = request.getHeader("X-Forwarded-For");
        report.put("ip", limit(forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim(), 50));
        report.put("userAgent", limit(request.getHeader("User-Agent"), 500));
        Object result = null;
        Throwable failure = null;
        try {
            result = point.proceed();
            return result;
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            int code = failureCode(result, failure);
            report.put("status", code == 200 ? 1 : 0);
            if (code != 200) report.put("failureCode", code);
            try {
                transport.internalPost("/internal/audit/operations", report);
            } catch (Exception auditError) {
                // 不把含 prompt、API key、SQL 的异常原文写进审计或日志。
                log.error("AI 管理操作审计上报失败: method={}, path={}", request.getMethod(), request.getRequestURI());
            }
        }
    }

    private int failureCode(Object result, Throwable error) {
        if (error instanceof AccessDeniedException) return 403;
        if (error instanceof ResponseStatusException response) return response.getStatusCode().value();
        if (error instanceof AIServiceException.RequestException) return 400;
        if (error instanceof AIServiceException.ConnectionException || error instanceof AIServiceException.ModelException) return 503;
        if (error instanceof AIServiceException.TimeoutException) return 408;
        if (error != null) return 500;
        if (result instanceof ResponseEntity<?> response) {
            return response.getStatusCode().is2xxSuccessful() ? failureCode(response.getBody(), null) : response.getStatusCode().value();
        }
        if (result instanceof Map<?, ?> map) {
            Object code = map.get("code");
            if (Boolean.FALSE.equals(map.get("success"))) return code instanceof Number number && number.intValue() != 200 ? number.intValue() : 400;
            if (code instanceof Number number && number.intValue() != 200) return number.intValue();
        }
        return 200;
    }

    private static String limit(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }
}
