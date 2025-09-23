package chat.liuxin.ai.resp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.Map;

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
     * 用户ID
     * 标识本次对话的用户
     */
    private String userId;
    
    /**
     * 使用的AI模型名称
     */
    private String model;
    
    /**
     * 历史消息数量
     * 表示当前用户的聊天历史记录条数
     */
    private Integer historyCount;
    
    /**
     * 响应时间戳
     * 服务器处理完成的时间
     */
    private Long timestamp;
    
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

    /**
     * 元数据
     * 用于扩展，例如上下文信息、文章ID、推荐内容等
     */
    private Map<String, Object> metadata;
    
    /**
     * 创建成功响应的便捷方法
     * 
     * @param message AI回复内容
     * @param userId 用户ID
     * @param model 模型名称
     * @param historyCount 历史消息数量
     * @return 成功响应对象
     */
    public static ChatResponse success(String message, String userId, String model, Integer historyCount) {
        return ChatResponse.builder()
                .success(true)
                .message(message)
                .userId(userId)
                .model(model)
                .historyCount(historyCount)
                .timestamp(System.currentTimeMillis())
                .responseLength(message != null ? message.length() : 0)
                .build();
    }
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
                .timestamp(System.currentTimeMillis())
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
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建失败响应的便捷方法（带用户ID）
     * 
     * @param errorMessage 错误信息
     * @param userId 用户ID
     * @return 失败响应对象
     */
    public static ChatResponse error(String errorMessage, String userId) {
        return ChatResponse.builder()
                .success(false)
                .message(errorMessage)
                .userId(userId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}