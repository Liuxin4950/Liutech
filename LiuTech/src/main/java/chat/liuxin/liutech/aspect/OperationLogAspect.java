package chat.liuxin.liutech.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;

import chat.liuxin.liutech.model.AdminLogs;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.service.LogService;
import chat.liuxin.liutech.utils.UserUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 操作日志切面
 * 自动记录管理端操作日志
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Lazy
    private final LogService logService;
    private final UserUtils userUtils;

    public OperationLogAspect(LogService logService, UserUtils userUtils) {
        this.logService = logService;
        this.userUtils = userUtils;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;

        try {
            return point.proceed();
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            try {
                recordOperationLog(operationLog, success, errorMessage);
            } catch (Exception e) {
                log.error("记录操作日志失败", e);
            }
        }
    }

    private void recordOperationLog(OperationLog operationLog, boolean success, String errorMessage) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        AdminLogs logEntry = new AdminLogs();
        logEntry.setAction(operationLog.action());
        logEntry.setTargetType(operationLog.targetType());

        Users currentUser = userUtils.getCurrentUser();
        if (currentUser != null) {
            logEntry.setOperator(currentUser.getUsername());
            logEntry.setOperatorId(currentUser.getId());
        } else {
            logEntry.setOperator("未知用户");
        }

        if (request != null) {
            logEntry.setIp(getClientIp(request));
            logEntry.setUserAgent(request.getHeader("User-Agent"));
        }

        logEntry.setStatus(success ? 1 : 0);

        if (!success && errorMessage != null) {
            logEntry.setErrorMessage(errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);
        }

        logEntry.setTargetName(StringUtils.isBlank(operationLog.targetName()) ? null : operationLog.targetName());

        String description = operationLog.description();
        if (StringUtils.isBlank(description)) {
            description = operationLog.action() + " " + operationLog.targetType();
        }
        if (!success) {
            description = description + " (失败)";
        }
        logEntry.setDescription(description);

        logService.saveLog(logEntry);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
