package chat.liuxin.ai.resp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 健康检查响应类
 * 用于返回AI服务的健康状态
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthResponse {
    
    /**
     * 请求是否成功
     */
    private Boolean success;
    
    /**
     * 服务状态
     * healthy: 健康
     * unhealthy: 不健康
     * degraded: 降级服务
     */
    private String status;
    
    /**
     * 状态描述信息
     */
    private String message;
    
    /**
     * 检查时间戳
     */
    private Long timestamp;
    
    /**
     * 响应时间（毫秒）
     * AI模型响应测试请求的时间
     */
    private Long responseTime;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 使用的AI模型信息
     */
    private String model;
    
    /**
     * 创建健康响应的便捷方法
     * 
     * @param responseTime AI模型响应时间
     * @return 健康响应对象
     */
    public static HealthResponse healthy(Long responseTime) {
        return HealthResponse.builder()
                .success(true)
                .status("healthy")
                .message("AI服务运行正常")
                .timestamp(System.currentTimeMillis())
                .responseTime(responseTime)
                .version("1.0.0")
                .build();
    }
    
    /**
     * 创建不健康响应的便捷方法
     * 
     * @param errorMessage 错误信息
     * @return 不健康响应对象
     */
    public static HealthResponse unhealthy(String errorMessage) {
        return HealthResponse.builder()
                .success(false)
                .status("unhealthy")
                .message("AI服务异常: " + errorMessage)
                .timestamp(System.currentTimeMillis())
                .version("1.0.0")
                .build();
    }
}