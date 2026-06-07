package chat.liuxin.liutech.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AOP配置类
 * 启用AspectJ自动代理，支持基于注解的切面编程
 * 
 * 功能说明：启用 AspectJ 自动代理，支持 @OperationLog 操作审计切面
 * 
 * 
 * 作者：刘鑫
 * 时间：2025-01-31
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {
    
    
}
