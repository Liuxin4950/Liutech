package chat.liuxin.liutech.aspect;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.AdminLogs;
import chat.liuxin.liutech.resp.LoginResp;
import chat.liuxin.liutech.resp.UserResp;
import chat.liuxin.liutech.service.LogService;
import chat.liuxin.liutech.utils.JwtUtil;
import chat.liuxin.liutech.utils.UserUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 现有注解提供业务语义，Controller 写请求兜底防止漏注解；不采集请求体。 */
@Slf4j
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class OperationLogAspect {
    private static final String ACTIVE = OperationLogAspect.class.getName() + ".active";
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();
    private static final Pattern VARIABLE = Pattern.compile("#[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*");
    private static final Map<String, String> TARGETS = Map.ofEntries(
            Map.entry("posts", "post"), Map.entry("users", "user"), Map.entry("user", "user"),
            Map.entry("comments", "comment"), Map.entry("categories", "category"), Map.entry("tags", "tag"),
            Map.entry("series", "series"), Map.entry("messages", "message"), Map.entry("carousels", "carousel"),
            Map.entry("announcements", "announcement"), Map.entry("resources", "resource"), Map.entry("upload", "resource"),
            Map.entry("images", "image"), Map.entry("music", "music"), Map.entry("points", "points"),
            Map.entry("settings", "system_setting"), Map.entry("about", "about"), Map.entry("community", "community"));
    private static final Map<String, String> TARGET_LABELS = Map.ofEntries(
            Map.entry("post", "文章"), Map.entry("user", "用户"), Map.entry("comment", "评论"),
            Map.entry("category", "分类"), Map.entry("tag", "标签"), Map.entry("series", "系列"),
            Map.entry("message", "留言"), Map.entry("carousel", "轮播图"), Map.entry("announcement", "公告"),
            Map.entry("resource", "资源"), Map.entry("image", "图片"), Map.entry("music", "音乐"),
            Map.entry("points", "积分"), Map.entry("system_setting", "系统设置"), Map.entry("about", "关于页"),
            Map.entry("community", "社区互动"));
    private final LogService logService;
    private final UserUtils userUtils;
    private final JwtUtil jwtUtil;

    @Around("execution(public * chat.liuxin.liutech.controller.admin..*(..)) || "
            + "execution(public * chat.liuxin.liutech.controller.web..*(..)) || "
            + "@annotation(chat.liuxin.liutech.aspect.OperationLog)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);
        if (request == null || !WRITES.contains(request.getMethod()) || request.getRequestURI().startsWith("/internal/")
                || request.getRequestURI().matches("/posts/\\d+/view") || request.getAttribute(ACTIVE) != null) {
            return point.proceed();
        }
        request.setAttribute(ACTIVE, Boolean.TRUE);
        AdminLogs entry = snapshot(point, annotation, request);
        Object result = null;
        Throwable failure = null;
        try {
            result = point.proceed();
            return result;
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            try {
                int code = failureCode(result, failure);
                entry.setStatus(code == 200 ? 1 : 0);
                if (code != 200) entry.setErrorMessage("操作未完成（错误码 " + code + "）");
                if (code == 200 && result instanceof Result<?> response) {
                    if (response.getData() instanceof LoginResp login) {
                        JwtUtil.TokenIdentity identity = jwtUtil.parseIdentity(login.getToken());
                        if (identity != null) {
                            entry.setOperatorId(identity.userId());
                            entry.setOperator(identity.username());
                        }
                    } else if ("register".equals(entry.getAction()) && response.getData() instanceof UserResp user) {
                        entry.setOperatorId(user.getId());
                        entry.setOperator(user.getUsername());
                    }
                }
                if (!logService.saveLog(entry)) log.error("操作审计未保存: action={}, targetType={}", entry.getAction(), entry.getTargetType());
            } catch (Exception auditError) {
                log.error("操作审计保存失败: action={}, targetType={}", entry.getAction(), entry.getTargetType());
            }
            // 保留标记：一次 HTTP 请求即使经过多个带注解的方法，也只记一条审计。
        }
    }

    private AdminLogs snapshot(ProceedingJoinPoint point, OperationLog annotation, HttpServletRequest request) {
        AdminLogs entry = new AdminLogs();
        entry.setOperatorId(userUtils.getCurrentUserId());
        String username = userUtils.getCurrentUsername();
        entry.setOperator(username == null ? "未认证访问" : username);
        entry.setIp(limit(clientIp(request), 50));
        entry.setUserAgent(limit(request.getHeader("User-Agent"), 500));
        String path = request.getRequestURI().replaceFirst("^/admin/", "/");
        String segment = path.split("/").length > 1 ? path.split("/")[1] : "system";
        String target = TARGETS.getOrDefault(segment, segment);
        String action = switch (request.getMethod()) {
            case "DELETE" -> "delete";
            case "PUT", "PATCH" -> "update";
            default -> "create";
        };
        if (path.equals("/user/login") || path.equals("/user/login/email/verify")) action = "login";
        else if (path.equals("/user/register")) action = "register";
        else if (path.contains("checkin")) action = "checkin";
        else if (path.contains("purchase")) action = "purchase";
        else if (path.contains("/like")) action = "like";
        else if (path.contains("/favorite")) action = "favorite";
        else if (path.contains("restore")) action = "restore";
        else if (path.startsWith("/upload/")) action = "upload";
        else if (path.equals("/user/reset-password")) action = "update";
        else if (path.equals("/user/forgot-password") || path.contains("/send-code") || path.endsWith("/login/email/send")) action = "verify";
        entry.setAction(annotation == null ? action : annotation.action());
        entry.setTargetType(annotation == null ? target : annotation.targetType());
        StandardEvaluationContext context = context(point);
        String description = annotation == null ? null : resolve(annotation.description(), context);
        if (annotation == null) {
            description = switch (path) {
                case "/user/register/send-code" -> "申请注册验证码";
                case "/user/forgot-password" -> "申请密码重置验证码";
                case "/user/reset-password" -> "重置用户密码";
                case "/user/login/email/send" -> "申请邮箱登录验证码";
                default -> null;
            };
        }
        String targetName = annotation == null ? null : resolve(annotation.targetName(), context);
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if ((targetName == null || targetName.isBlank()) && variables instanceof Map<?, ?> vars) {
            targetName = vars.values().stream().filter(v -> v != null && v.toString().matches("[0-9a-fA-F-]{1,64}"))
                    .map(Object::toString).findFirst().orElse(null);
        }
        entry.setTargetName(limit(targetName, 255));
        entry.setDescription(limit(description == null || description.isBlank()
                ? actionLabel(entry.getAction()) + TARGET_LABELS.getOrDefault(entry.getTargetType(), "业务数据")
                : description, 1000));
        return entry;
    }

    private String actionLabel(String action) {
        return switch (action) {
            case "delete" -> "删除";
            case "update" -> "更新";
            case "login" -> "登录：";
            case "register" -> "注册";
            case "restore" -> "恢复";
            case "like" -> "切换点赞：";
            case "favorite" -> "切换收藏：";
            case "upload" -> "上传";
            case "purchase" -> "购买";
            case "checkin" -> "签到：";
            default -> "创建";
        };
    }

    private int failureCode(Object result, Throwable failure) {
        if (failure instanceof BusinessException business) return business.getCode();
        if (failure instanceof AccessDeniedException) return 403;
        if (failure != null) return 500;
        if (result instanceof Result<?> response) return response.getCode();
        if (result instanceof ResponseEntity<?> response) {
            if (!response.getStatusCode().is2xxSuccessful()) return response.getStatusCode().value();
            return failureCode(response.getBody(), null);
        }
        if (result instanceof Map<?, ?> map) {
            if (Boolean.FALSE.equals(map.get("success")) || map.containsKey("error")) return 400;
            if (map.get("code") instanceof Number number && number.intValue() != 200) return number.intValue();
        }
        return 200;
    }

    private StandardEvaluationContext context(ProceedingJoinPoint point) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] names = NAMES.getParameterNames(((MethodSignature) point.getSignature()).getMethod());
        Object[] args = point.getArgs();
        if (names != null) for (int i = 0; i < names.length; i++) context.setVariable(names[i], args[i]);
        return context;
    }

    private String resolve(String expression, StandardEvaluationContext context) {
        if (expression == null || expression.isBlank() || !expression.contains("#")) return expression;
        try {
            if (expression.startsWith("#")) {
                Object value = PARSER.parseExpression(expression).getValue(context);
                return value == null ? null : value.toString();
            }
            return VARIABLE.matcher(expression).replaceAll(match -> {
                Object value = PARSER.parseExpression(match.group()).getValue(context);
                return java.util.regex.Matcher.quoteReplacement(value == null ? "" : value.toString());
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String real = request.getHeader("X-Real-IP");
        return real != null && !real.isBlank() ? real : request.getRemoteAddr();
    }

    private static String limit(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }
}
