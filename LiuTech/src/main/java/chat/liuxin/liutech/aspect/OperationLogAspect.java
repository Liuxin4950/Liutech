package chat.liuxin.liutech.aspect;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
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
 * 自动记录管理端操作日志，支持 SpEL 表达式解析 targetName 和 description
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Lazy
    private final LogService logService;
    private final UserUtils userUtils;

    public OperationLogAspect(LogService logService, UserUtils userUtils) {
        this.logService = logService;
        this.userUtils = userUtils;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
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
                recordOperationLog(point, operationLog, success, errorMessage);
            } catch (Exception e) {
                log.error("记录操作日志失败", e);
            }
        }
    }

    private void recordOperationLog(ProceedingJoinPoint point, OperationLog operationLog, boolean success, String errorMessage) {
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

        EvaluationContext evalContext = createEvaluationContext(point);

        String targetName = resolveExpression(operationLog.targetName(), evalContext);
        logEntry.setTargetName(StringUtils.isBlank(targetName) ? null : targetName);

        String description = resolveExpression(operationLog.description(), evalContext);
        if (StringUtils.isBlank(description)) {
            description = operationLog.action() + " " + operationLog.targetType();
        }
        if (!success) {
            description = description + " (失败)";
        }
        logEntry.setDescription(description);

        logService.saveLog(logEntry);
    }

    /**
     * 创建 SpEL 评估上下文，将方法参数注入为变量
     */
    private EvaluationContext createEvaluationContext(ProceedingJoinPoint point) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        if (method != null) {
            String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
            Object[] args = point.getArgs();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
        }
        return context;
    }

    /**
     * 解析 SpEL 表达式，非 SpEL 表达式原样返回
     */
    private String resolveExpression(String expression, EvaluationContext context) {
        if (StringUtils.isBlank(expression) || context == null) {
            return expression;
        }
        if (!expression.contains("#")) {
            return expression;
        }
        try {
            Object value = PARSER.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("SpEL 表达式解析失败: {}", expression, e);
            return expression;
        }
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
