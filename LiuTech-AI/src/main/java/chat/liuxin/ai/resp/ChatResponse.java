package chat.liuxin.ai.resp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.Map;

import groovy.util.logging.Log;

/**
 * AI聊天响应类
 * 用于返回AI聊天的结果数据
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    
    /**
     * 请求是否成功
     * true: 成功，false: 失败
     */
    private Boolean success;
    
    /**
     * 响应消息
     * 成功时为AI回复内容，失败时为错误信息
     */
    private String message;
    
    
    /**
     * 使用的AI模型名称
     */
    private String model;
    
    
    /**
     * 响应耗时（毫秒）
     * AI处理请求所花费的时间
     */
    private Long processingTime;
    
    /**
     * 响应长度
     * AI回复内容的字符数
     */
    private Integer responseLength;

    // ================== 扩展字段：情绪/动作/元数据 ==================
    /**
     * 情绪标签
     * 例如: happy, sad, angry, thinking, neutral
     */
    private String emotion;

    /**
     * 动作指令
     * 用于前端执行页面跳转、收藏文章等
     * 例如: open_latest_articles, favorite_article, open_home
     */
    private String action;

    private Long conversationId;
    
    
    /**
     * 创建成功响应的便捷方法
     * 
     * @param message 响应消息
     * @return 成功响应对象
     */
    public static ChatResponse success(String message) {
        return ChatResponse.builder()
                .success(true)
                .message(message)
                .build();
    }
    
    /**
     * 创建失败响应的便捷方法
     * 
     * @param errorMessage 错误信息
     * @return 失败响应对象
     */
    public static ChatResponse error(String errorMessage) {
        return ChatResponse.builder()
                .success(false)
                .message(errorMessage)
                .build();
    }
}
