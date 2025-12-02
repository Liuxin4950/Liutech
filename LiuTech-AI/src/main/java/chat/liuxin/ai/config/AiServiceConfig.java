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
 * AI服务统一配置类
 * 
 * 功能：
 * 1. 配置重试模板，支持指数退避策略
 * 2. 配置优化的线程池，提高并发处理能力
 * 3. 提供统一的错误处理策略
 * 
 * 作者：刘鑫
 * 时间：2025-12-02
 */
@Slf4j
@Configuration
@EnableRetry
@EnableAsync
public class AiServiceConfig {

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
     * 优化的AI任务线程池
     * 增加核心线程数和最大线程数，提高并发处理能力
     * 注意：聊天系统实时性要求高，不适合使用缓存
     */
    @Bean(name = "optimizedAiTaskExecutor")
    public Executor optimizedAiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20); // 增加核心线程数
        executor.setMaxPoolSize(100); // 增加最大线程数
        executor.setQueueCapacity(500); // 增加队列容量
        executor.setKeepAliveSeconds(60); // 线程空闲时间60秒
        executor.setThreadNamePrefix("Optimized-AI-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        
        log.info("优化AI任务线程池初始化完成：核心线程数={}, 最大线程数={}, 队列容量={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
}