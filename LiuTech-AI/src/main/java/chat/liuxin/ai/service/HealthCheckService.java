package chat.liuxin.ai.service;

import chat.liuxin.ai.exception.AIServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI服务健康检查服务
 * 
 * 功能：
 * 1. 定期检查AI服务的可用性
 * 2. 监控服务响应时间和成功率
 * 3. 提供熔断器功能，避免级联故障
 * 4. 记录服务健康状态历史
 * 
 * 作者：刘鑫
 * 时间：2025-09-24
 */
@Slf4j
@Service
public class HealthCheckService {
    
    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${ai.health-check.enabled:true}")
    private boolean healthCheckEnabled;
    
    @Value("${ai.health-check.timeout:5000}")
    private int healthCheckTimeout;
    
    @Value("${ai.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;
    
    @Value("${ai.circuit-breaker.recovery-timeout:30000}")
    private long recoveryTimeout;
    
    @Autowired
    private RestTemplate restTemplate;
    
    // 服务健康状态
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
    
    // 性能统计
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    
    /**
     * 定期健康检查 - 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void performHealthCheck() {
        if (!healthCheckEnabled) {
            return;
        }
        
        log.debug("开始执行AI服务健康检查");
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 发送健康检查请求
            String healthUrl = ollamaBaseUrl + "/api/tags";
            restTemplate.getForObject(healthUrl, String.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 记录成功
            recordSuccess(responseTime);
            log.debug("AI服务健康检查成功，响应时间: {}ms", responseTime);
            
        } catch (Exception e) {
            // 记录失败
            recordFailure(e);
            log.warn("AI服务健康检查失败: {}", e.getMessage());
        }
    }
    
    /**
     * 异步健康检查
     */
    @Async("healthCheckExecutor")
    public CompletableFuture<Boolean> checkHealthAsync() {
        try {
            long startTime = System.currentTimeMillis();
            String healthUrl = ollamaBaseUrl + "/api/tags";
            restTemplate.getForObject(healthUrl, String.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            recordSuccess(responseTime);
            return CompletableFuture.completedFuture(true);
            
        } catch (Exception e) {
            recordFailure(e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * 检查服务是否可用（熔断器逻辑）
     */
    public boolean isServiceAvailable() {
        // 如果健康检查被禁用，默认认为服务可用
        if (!healthCheckEnabled) {
            return true;
        }
        
        // 如果当前状态健康，直接返回
        if (isHealthy.get()) {
            return true;
        }
        
        // 如果处于熔断状态，检查是否到了恢复时间
        long currentTime = System.currentTimeMillis();
        long timeSinceLastFailure = currentTime - lastFailureTime.get();
        
        if (timeSinceLastFailure > recoveryTimeout) {
            log.info("熔断器恢复时间已到，尝试恢复服务");
            // 重置失败计数，给服务一次机会
            consecutiveFailures.set(0);
            isHealthy.set(true);
            return true;
        }
        
        return false;
    }
    
    /**
     * 在调用AI服务前检查可用性
     */
    public void checkServiceAvailability() throws AIServiceException {
        if (!isServiceAvailable()) {
            throw new AIServiceException.ConnectionException(
                String.format("AI服务当前不可用，连续失败%d次，将在%d秒后重试", 
                    consecutiveFailures.get(),
                    (recoveryTimeout - (System.currentTimeMillis() - lastFailureTime.get())) / 1000)
            );
        }
    }
    
    /**
     * 记录成功请求
     */
    private void recordSuccess(long responseTime) {
        totalRequests.incrementAndGet();
        successfulRequests.incrementAndGet();
        totalResponseTime.addAndGet(responseTime);
        lastSuccessTime.set(System.currentTimeMillis());
        
        // 重置失败计数
        consecutiveFailures.set(0);
        isHealthy.set(true);
    }
    
    /**
     * 记录失败请求
     */
    private void recordFailure(Exception e) {
        totalRequests.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        int failures = consecutiveFailures.incrementAndGet();
        
        // 如果连续失败次数超过阈值，触发熔断
        if (failures >= failureThreshold) {
            isHealthy.set(false);
            log.error("AI服务连续失败{}次，触发熔断器，服务将在{}秒后尝试恢复", 
                failures, recoveryTimeout / 1000);
        }
    }
    
    /**
     * 获取服务健康状态信息
     */
    public HealthStatus getHealthStatus() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        double successRate = total > 0 ? (double) successful / total * 100 : 0;
        double avgResponseTime = successful > 0 ? (double) totalResponseTime.get() / successful : 0;
        
        return HealthStatus.builder()
            .healthy(isHealthy.get())
            .consecutiveFailures(consecutiveFailures.get())
            .totalRequests(total)
            .successfulRequests(successful)
            .successRate(successRate)
            .averageResponseTime(avgResponseTime)
            .lastSuccessTime(LocalDateTime.ofEpochSecond(lastSuccessTime.get() / 1000, 0, java.time.ZoneOffset.ofHours(8)))
            .lastFailureTime(lastFailureTime.get() > 0 ? 
                LocalDateTime.ofEpochSecond(lastFailureTime.get() / 1000, 0, java.time.ZoneOffset.ofHours(8)) : null)
            .build();
    }
    
    /**
     * 健康状态数据类
     */
    @lombok.Builder
    @lombok.Data
    public static class HealthStatus {
        private boolean healthy;
        private int consecutiveFailures;
        private long totalRequests;
        private long successfulRequests;
        private double successRate;
        private double averageResponseTime;
        private LocalDateTime lastSuccessTime;
        private LocalDateTime lastFailureTime;
    }
}