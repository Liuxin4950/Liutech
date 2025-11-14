package chat.liuxin.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 错误处理和重试配置
 * 
 * 功能：
 * 1. 配置重试模板，支持指数退避策略
 * 2. 配置异步执行器，用于健康检查和后台任务
 * 3. 提供统一的错误处理策略
 * 
 * 作者：刘鑫
 * 时间：2025-09-24
 */
@Slf4j
@Configuration
@EnableRetry
@EnableAsync
public class ErrorHandlingConfig {

    /**
     * 重试模板配置
     * 支持指数退避策略，避免对故障服务造成压力
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // 重试策略：最多重试3次
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // 退避策略：指数退避，初始延迟1秒，最大延迟10秒
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L);
        backOffPolicy.setMaxInterval(10000L);
        backOffPolicy.setMultiplier(2.0);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        // 添加重试监听器
        retryTemplate.registerListener(new org.springframework.retry.RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(
                    org.springframework.retry.RetryContext context, 
                    org.springframework.retry.RetryCallback<T, E> callback, 
                    Throwable throwable) {
                log.warn("重试执行失败，第{}次重试，错误：{}", 
                    context.getRetryCount(), throwable.getMessage());
            }
        });
        
        return retryTemplate;
    }
    
    /**
     * 异步任务执行器配置
     * 用于健康检查、后台任务等异步操作
     */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AI-Task-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    
}