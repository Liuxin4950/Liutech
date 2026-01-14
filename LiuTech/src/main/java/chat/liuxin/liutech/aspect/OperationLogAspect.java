package chat.liuxin.liutech.aspect;

import java.util.HashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
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
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * 操作日志切面
 * 自动记录管理端操作日志
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Lazy// 延迟加载，避免循环依赖
    private final LogService logService;
    private final UserUtils userUtils;
    // SpEL表达式解析器
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public OperationLogAspect(LogService logService, UserUtils userUtils) {
        this.logService = logService;
        this.userUtils = userUtils;
    }

    /**
     * 环绕通知，拦截所有带@OperationLog注解的方法
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        log.debug("OperationLog切面拦截到方法: {}.{}",
            point.getSignature().getDeclaringTypeName(),
            point.getSignature().getName());

        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        Object result = null;

        try {
            // 执行目标方法
            result = point.proceed();
            log.debug("方法执行成功，返回结果: {}", result);
            return result;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.debug("方法执行失败: {}", errorMessage);
            throw e;
        } finally {
            // 记录日志
            try {
                log.debug("准备记录操作日志: action={}, targetType={}", operationLog.action(), operationLog.targetType());
                recordOperationLog(point, operationLog, success, errorMessage, result, System.currentTimeMillis() - startTime);
                log.debug("操作日志记录完成");
            } catch (Exception e) {
                log.error("记录操作日志失败", e);
            }
        }
    }

    /**
     * 记录操作日志
     */
    private void recordOperationLog(ProceedingJoinPoint point, OperationLog operationLog,
            boolean success, String errorMessage, Object result, long executionTime) {
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // 构建日志对象
        AdminLogs logEntry = new AdminLogs();

        // 设置操作类型和目标类型
        logEntry.setAction(operationLog.action());
        logEntry.setTargetType(operationLog.targetType());

        // 设置操作人信息（从UserUtils获取）
        Users currentUser = userUtils.getCurrentUser();
        if (currentUser != null) {
            logEntry.setOperator(currentUser.getUsername());
            logEntry.setOperatorId(currentUser.getId());
        } else {
            logEntry.setOperator("未知用户");
            logEntry.setOperatorId(null);
        }

        // 设置IP地址
        if (request != null) {
            logEntry.setIp(getClientIP(request));
            logEntry.setUserAgent(request.getHeader("User-Agent"));
        }

        // 设置状态
        logEntry.setStatus(success ? 1 : 0);

        // 设置错误信息
        if (!success && errorMessage != null) {
            logEntry.setErrorMessage(errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);
        }

        // 设置目标名称
        String targetName = resolveTargetName(point, operationLog, result);
        logEntry.setTargetName(targetName);

        // 设置操作描述
        String description = resolveDescription(point, operationLog, result, success);
        logEntry.setDescription(description);

        // 保存日志
        logService.saveLog(logEntry);
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时，第一个IP为真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 解析目标名称
     */
    @Nullable
    private String resolveTargetName(ProceedingJoinPoint point, OperationLog operationLog, Object result) {
        String targetNameSpel = operationLog.targetName();
        if (StringUtils.isBlank(targetNameSpel)) {
            return null;
        }

        try {
            EvaluationContext context = createEvaluationContext(point, result);
            return expressionParser.parseExpression(targetNameSpel).getValue(context, String.class);
        } catch (Exception e) {
            log.debug("解析目标名称SpEL表达式失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析操作描述
     */
    private String resolveDescription(ProceedingJoinPoint point, OperationLog operationLog, Object result, boolean success) {
        String description = operationLog.description();

        // 如果描述为空，生成默认描述
        if (StringUtils.isBlank(description)) {
            description = String.format("%s %s",
                    getActionDisplayName(operationLog.action()),
                    getTargetTypeDisplayName(operationLog.targetType()));
        } else {
            // 尝试解析SpEL表达式（只解析纯SpEL表达式，包含冒号的直接返回原描述）
            try {
                // 如果描述包含冒号且不是纯SpEL，则不解析，直接使用原描述
                if (description.contains(":") && !description.trim().startsWith("#")) {
                    // 描述中可能包含普通文本，直接使用
                    log.debug("描述包含冒号，视为普通文本，不进行SpEL解析");
                } else {
                    // 纯SpEL表达式，尝试解析
                    EvaluationContext context = createEvaluationContext(point, result);
                    String parsedValue = expressionParser.parseExpression(description).getValue(context, String.class);
                    if (parsedValue != null) {
                        description = parsedValue;
                    }
                }
            } catch (Exception e) {
                log.debug("解析描述SpEL表达式失败，使用原描述: {}", e.getMessage());
            }
        }

        // 如果失败，添加失败标记
        if (!success && description != null) {
            description = description + " (失败)";
        }

        return description;
    }

    /**
     * 创建SpEL表达式上下文
     */
    @NonNull
    private EvaluationContext createEvaluationContext(ProceedingJoinPoint point, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 添加方法参数
        MethodSignature signature = (MethodSignature) point.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = point.getArgs();
        if (paramNames != null && args != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        // 添加结果
        context.setVariable("result", result);

        // 添加方法对象
        context.setVariable("method", signature.getMethod());

        return context;
    }

    /**
     * 获取操作类型显示名称
     */
    private String getActionDisplayName(String action) {
        Map<String, String> actionMap = new HashMap<>();
        actionMap.put("login", "登录");
        actionMap.put("create", "创建");
        actionMap.put("update", "更新");
        actionMap.put("delete", "删除");
        actionMap.put("restore", "恢复");
        actionMap.put("publish", "发布");
        actionMap.put("offline", "下线");
        actionMap.put("enable", "启用");
        actionMap.put("disable", "禁用");
        actionMap.put("upload", "上传");
        actionMap.put("download", "下载");
        return actionMap.getOrDefault(action.toLowerCase(), action);
    }

    /**
     * 获取目标类型显示名称
     */
    private String getTargetTypeDisplayName(String targetType) {
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("post", "文章");
        typeMap.put("user", "用户");
        typeMap.put("category", "分类");
        typeMap.put("tag", "标签");
        typeMap.put("announcement", "公告");
        typeMap.put("comment", "评论");
        typeMap.put("resource", "资源");
        typeMap.put("music", "音乐");
        return typeMap.getOrDefault(targetType.toLowerCase(), targetType);
    }
}
