package chat.liuxin.ai.resp;

import chat.liuxin.ai.entity.AiChatMessage;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * 聊天历史记录响应类
 * 用于返回分页查询的聊天历史记录
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistoryResponse {
    
    /** 是否成功 */
    private boolean success;
    
    /** 错误消息（失败时） */
    private String message;
    
    /** 聊天历史记录列表 */
    private List<AiChatMessage> data;
    
    /** 当前页码 */
    private int page;
    
    /** 每页大小 */
    private int size;
    
    /** 总记录数 */
    private long total;
    
    /** 总页数 */
    private int totalPages;
    
    /** 用户ID */
    private String userId;
    
    /** 响应时间戳 */
    private long timestamp;
    
    /**
     * 创建成功响应的便捷方法
     * 
     * @param data 聊天历史记录列表
     * @param page 当前页码
     * @param size 每页大小
     * @param total 总记录数
     * @param userId 用户ID
     * @return 成功响应对象
     */
    public static ChatHistoryResponse success(List<AiChatMessage> data, int page, int size, long total, String userId) {
        int totalPages = (int) Math.ceil((double) total / size);
        return ChatHistoryResponse.builder()
                .success(true)
                .data(data)
                .page(page)
                .size(size)
                .total(total)
                .totalPages(totalPages)
                .userId(userId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建失败响应的便捷方法
     * 
     * @param message 错误消息
     * @return 失败响应对象
     */
    public static ChatHistoryResponse error(String message) {
        return ChatHistoryResponse.builder()
                .success(false)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}