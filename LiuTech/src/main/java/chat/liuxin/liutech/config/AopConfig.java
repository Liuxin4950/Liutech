package chat.liuxin.liutech.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AOP配置类
 * 启用AspectJ自动代理，支持基于注解的切面编程
 * 
 * 功能说明：
 * 1. 启用AspectJ自动代理功能
 * 2. 配置使用CGLIB代理（proxyTargetClass = true）
 * 3. 支持切面编程，包括：
 *    - 请求计时切面（RequestTimingAspect）
 *    - 其他业务切面
 * 
 * 配置说明：
 * - @EnableAspectJAutoProxy：启用AspectJ自动代理
 * - proxyTargetClass = true：强制使用CGLIB代理而不是JDK动态代理
 *   这样可以代理没有实现接口的类，提供更好的兼容性
 * 
 * 作者: 刘鑫
 * 时间: 2025-01-31
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {
    
    // AOP配置类主要通过注解进行配置
    // 具体的切面逻辑在各个Aspect类中实现
    // 当前项目包含的切面：
    // 1. RequestTimingAspect - 请求计时和日志记录切面
    
}