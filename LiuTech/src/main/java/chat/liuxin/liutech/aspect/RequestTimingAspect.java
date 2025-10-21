package chat.liuxin.liutech.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * 请求计时切面
 * 用于记录所有Controller请求的执行时间和基本信息
 *
 * 功能：
 * 1. 记录请求开始时间
 * 2. 记录请求结束时间
 * 3. 计算请求总耗时
 * 4. 记录请求的基本信息（URL、方法、参数等）
 *
 * 作者: 刘鑫
 * 时间: 2025-09-31
 */
@Slf4j
@Aspect
@Component
public class RequestTimingAspect {

    /**
     * 环绕通知：拦截所有Controller中的方法
     * 切点表达式说明：
     * - execution(* chat.liuxin.liutech.controller..*.*(..))：匹配controller包及其子包下的所有方法
     * - @annotation(org.springframework.web.bind.annotation.RequestMapping)：匹配带有@RequestMapping注解的方法
     * - @annotation(org.springframework.web.bind.annotation.GetMapping)：匹配带有@GetMapping注解的方法
     * - @annotation(org.springframework.web.bind.annotation.PostMapping)：匹配带有@PostMapping注解的方法
     * - @annotation(org.springframework.web.bind.annotation.PutMapping)：匹配带有@PutMapping注解的方法
     * - @annotation(org.springframework.web.bind.annotation.DeleteMapping)：匹配带有@DeleteMapping注解的方法
     */
    @Around("execution(* chat.liuxin.liutech.controller..*.*(..)) || " +
            "@annotation(org.springframework.web.bind.annotation.RequestMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = null;
        String requestUrl = "Unknown";
        String httpMethod = "Unknown";
        String clientIp = "Unknown";

        if (attributes != null) {
            request = attributes.getRequest();
            requestUrl = request.getRequestURL().toString();
            httpMethod = request.getMethod();
            clientIp = getClientIpAddress(request);
        }

        // 获取方法信息
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        // 记录请求开始
        long startTime = System.currentTimeMillis();
        log.info("========== 请求开始 ==========");
        log.info("请求URL: {} {}", httpMethod, requestUrl);
        log.info("调用方法: {}.{}", className, methodName);
        log.info("客户端IP: {}", clientIp);

        // 记录请求参数（过滤敏感信息）
        if (args != null && args.length > 0) {
            String argsStr = Arrays.toString(args);
            // 简单的敏感信息过滤
            argsStr = filterSensitiveInfo(argsStr);
            log.info("请求参数: {}", argsStr);
        }

        log.info("开始时间: {}", new java.util.Date(startTime));

        Object result;
        boolean success = true;
        String errorMessage = null;

        try {
            // 执行目标方法
            result = joinPoint.proceed();
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("请求执行异常: {}", errorMessage, e);
            throw e; // 重新抛出异常，不影响原有的异常处理逻辑
        } finally {
            // 记录请求结束
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            log.info("========== 请求结束 耗时:{} 执行结果:{} ==========",executionTime,success ? "成功" : "失败");

            if (!success && errorMessage != null) {
                log.info("错误信息: {}", errorMessage);
            }

            // 性能警告
            if (executionTime > 3000) {
                log.warn("⚠️ 请求执行时间过长: {} ms，建议优化性能", executionTime);
            } else if (executionTime > 1000) {
                log.warn("⚠️ 请求执行时间较长: {} ms", executionTime);
            }

            log.info("================================");
        }

        return result;
    }

    /**
     * 获取客户端真实IP地址
     * 考虑代理服务器的情况
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // 多次反向代理后会有多个IP值，第一个IP才是真实IP
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * 过滤敏感信息
     * 简单的敏感信息过滤，避免在日志中暴露密码等敏感数据
     */
    private String filterSensitiveInfo(String input) {
        if (input == null) {
            return null;
        }

        // 过滤常见的敏感字段
        return input.replaceAll("(?i)(password|pwd|token|secret|key)=[^,\\]\\}]*", "$1=***")
                   .replaceAll("(?i)\"(password|pwd|token|secret|key)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"***\"");
    }
}
