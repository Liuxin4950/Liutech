package chat.liuxin.ai.resp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * 统计信息响应类
 * 用于返回AI服务的统计数据
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsResponse {
    
    /**
     * 请求是否成功
     */
    private Boolean success;
    
    /**
     * 错误信息（失败时使用）
     */
    private String message;
    
    /**
     * 当前活跃用户数量
     * 表示有聊天记忆的用户总数
     */
    private Integer activeUserCount;
    
    /**
     * 当前活跃会话数量
     * 表示所有用户的会话总数
     */
    private Integer activeSessionCount;
    
    /**
     * 服务器时间戳
     * 统计数据生成的时间
     */
    private Long serverTime;
    
    /**
     * 服务运行时长（毫秒）
     * 从服务启动到现在的时间
     */
    private Long uptime;
    
    /**
     * 总处理请求数（可选扩展）
     */
    private Long totalRequests;
    
    /**
     * 平均响应时间（毫秒，可选扩展）
     */
    private Double avgResponseTime;
    
    /**
     * 创建成功响应
     * 
     * @param activeUserCount 活跃用户数
     * @return 统计响应对象
     */
    public static StatsResponse success(Integer activeUserCount) {
        return StatsResponse.builder()
                .success(true)
                .activeUserCount(activeUserCount)
                .serverTime(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建成功响应（支持Map数据）
     * 
     * @param statsData 统计数据Map
     * @return 统计响应对象
     */
    public static StatsResponse success(Map<String, Object> statsData) {
        StatsResponse.StatsResponseBuilder builder = StatsResponse.builder()
                .success(true);
                
        if (statsData.containsKey("activeUserCount")) {
            builder.activeUserCount((Integer) statsData.get("activeUserCount"));
        }
        if (statsData.containsKey("activeSessionCount")) {
            builder.activeSessionCount((Integer) statsData.get("activeSessionCount"));
        }
        if (statsData.containsKey("timestamp")) {
            builder.serverTime((Long) statsData.get("timestamp"));
        } else {
            builder.serverTime(System.currentTimeMillis());
        }
        
        return builder.build();
    }
    
    /**
     * 创建失败响应的便捷方法
     * 
     * @param errorMessage 错误信息
     * @return 失败响应对象
     */
    public static StatsResponse error(String errorMessage) {
        return StatsResponse.builder()
                .success(false)
                .message(errorMessage)
                .serverTime(System.currentTimeMillis())
                .build();
    }
}