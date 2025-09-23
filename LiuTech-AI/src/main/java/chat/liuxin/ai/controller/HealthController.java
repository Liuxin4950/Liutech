package chat.liuxin.ai.controller;

import chat.liuxin.ai.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI服务健康检查控制器
 * 
 * 功能：
 * 1. 提供服务健康状态查询接口
 * 2. 支持外部监控系统集成
 * 3. 提供详细的性能统计信息
 * 
 * 作者：刘鑫
 * 时间：2025-09-24
 */
@Slf4j
@RestController
@RequestMapping("/ai/health")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class HealthController {
    
    private final HealthCheckService healthCheckService;
    
    /**
     * 简单健康检查接口
     * 返回服务是否可用的基本信息
     */
    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean isAvailable = healthCheckService.isServiceAvailable();
            HealthCheckService.HealthStatus status = healthCheckService.getHealthStatus();
            
            response.put("status", isAvailable ? "UP" : "DOWN");
            response.put("healthy", status.isHealthy());
            response.put("timestamp", System.currentTimeMillis());
            
            if (!isAvailable) {
                response.put("message", "AI服务当前不可用");
                response.put("consecutiveFailures", status.getConsecutiveFailures());
            }
            
        } catch (Exception e) {
            log.error("健康检查失败", e);
            response.put("status", "DOWN");
            response.put("healthy", false);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
        }
        
        return response;
    }
    
    /**
     * 详细健康检查接口
     * 返回完整的服务状态和性能统计
     */
    @GetMapping("/detailed")
    public Map<String, Object> detailedHealth() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean isAvailable = healthCheckService.isServiceAvailable();
            HealthCheckService.HealthStatus status = healthCheckService.getHealthStatus();
            
            response.put("status", isAvailable ? "UP" : "DOWN");
            response.put("healthy", status.isHealthy());
            response.put("consecutiveFailures", status.getConsecutiveFailures());
            response.put("totalRequests", status.getTotalRequests());
            response.put("successfulRequests", status.getSuccessfulRequests());
            response.put("successRate", String.format("%.2f%%", status.getSuccessRate()));
            response.put("averageResponseTime", String.format("%.2fms", status.getAverageResponseTime()));
            response.put("lastSuccessTime", status.getLastSuccessTime());
            response.put("lastFailureTime", status.getLastFailureTime());
            response.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            log.error("详细健康检查失败", e);
            response.put("status", "DOWN");
            response.put("healthy", false);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
        }
        
        return response;
    }
    
    /**
     * 手动触发健康检查
     * 立即执行一次健康检查并返回结果
     */
    @PostMapping("/check")
    public Map<String, Object> triggerHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("手动触发AI服务健康检查");
            
            // 异步执行健康检查
            boolean result = healthCheckService.checkHealthAsync().get();
            HealthCheckService.HealthStatus status = healthCheckService.getHealthStatus();
            
            response.put("checkResult", result);
            response.put("status", result ? "UP" : "DOWN");
            response.put("healthy", status.isHealthy());
            response.put("message", result ? "健康检查通过" : "健康检查失败");
            response.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            log.error("手动健康检查失败", e);
            response.put("checkResult", false);
            response.put("status", "DOWN");
            response.put("healthy", false);
            response.put("error", e.getMessage());
            response.put("message", "健康检查执行异常");
            response.put("timestamp", System.currentTimeMillis());
        }
        
        return response;
    }
}