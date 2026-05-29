package chat.liuxin.ai.infra.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 用于标记需要记录操作日志的方法
 *
 * 注意：LiuTech-AI 项目目前仅使用此注解作为标记，
 * 未配置切面处理。如需持久化日志，请参考主项目 LiuTech 的 OperationLogAspect 实现。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作类型
     * 如：create、update、delete、enable、disable 等
     */
    String action();

    /**
     * 目标类型
     * 如：ai_model 等
     */
    String targetType();

    /**
     * 操作描述
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
     */
    String targetName() default "";
}
