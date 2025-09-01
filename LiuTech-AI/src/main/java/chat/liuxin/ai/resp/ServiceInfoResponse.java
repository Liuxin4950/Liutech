package chat.liuxin.ai.resp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 服务信息响应类
 * 用于返回AI服务的基本信息
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceInfoResponse {
    
    /**
     * 服务名称
     */
    private String service;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 服务作者
     */
    private String author;
    
    /**
     * 服务描述
     */
    private String description;
    
    /**
     * 服务启动时间
     */
    private Long startTime;
    
    /**
     * 当前时间戳
     */
    private Long currentTime;
    
    /**
     * 支持的功能列表
     */
    private String[] features;
    
    /**
     * API文档地址
     */
    private String apiDocs;
    
    /**
     * 创建默认服务信息的便捷方法
     * 
     * @return 服务信息响应对象
     */
    public static ServiceInfoResponse defaultInfo() {
        return ServiceInfoResponse.builder()
                .service("LiuTech AI Service")
                .version("1.0.0")
                .author("刘鑫")
                .description("基于Spring AI + Ollama的智能助手")
                .currentTime(System.currentTimeMillis())
                .features(new String[]{
                    "AI聊天对话",
                    "流式响应",
                    "用户记忆管理",
                    "多模型支持",
                    "健康检查",
                    "统计信息"
                })
                .apiDocs("/api/ai/docs")
                .build();
    }
}