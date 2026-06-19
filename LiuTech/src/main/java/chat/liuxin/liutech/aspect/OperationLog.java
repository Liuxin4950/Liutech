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
     * 如：login、create、update、delete、restore、publish、offline等
     */
    String action();

    /**
     * 目标类型
     * 如：post、user、category、tag、announcement等
     */
    String targetType();

    /**
     * 操作描述（纯文本）
     */
    String description() default "";

    /**
     * 目标名称（纯文本标识，如文章标题、用户ID等）
     */
    String targetName() default "";
}
