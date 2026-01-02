package chat.liuxin.liutech.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 用于标记需要记录操作日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作类型
     * 如：登录、创建、更新、删除、恢复、发布、下线等
     */
    String action();

    /**
     * 目标类型
     * 如：post、user、category、tag、announcement等
     */
    String targetType();

    /**
     * 操作描述
     * 支持SpEL表达式，如："'创建用户: ' + #username"
     */
    String description() default "";

    /**
     * 是否记录参数
     * 默认为true
     */
    boolean logParams() default true;

    /**
     * 是否记录结果
     * 默认为true
     */
    boolean logResult() default true;

    /**
     * 目标名称的SpEL表达式
     * 如："#user.username" 或 "#post.title"
     */
    String targetName() default "";
}
