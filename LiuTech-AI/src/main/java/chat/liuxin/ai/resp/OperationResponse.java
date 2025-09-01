package chat.liuxin.ai.resp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 通用操作响应类
 * 用于返回各种操作的执行结果
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationResponse {
    
    /**
     * 操作是否成功
     */
    private Boolean success;
    
    /**
     * 操作结果消息
     * 成功时为操作成功信息，失败时为错误信息
     */
    private String message;
    
    /**
     * 操作类型
     * 如：clear_memory, delete_user, update_config等
     */
    private String operation;
    
    /**
     * 相关的用户ID（如果适用）
     */
    private String userId;
    
    /**
     * 操作时间戳
     */
    private Long timestamp;
    
    /**
     * 操作耗时（毫秒）
     */
    private Long processingTime;
    
    /**
     * 额外的操作数据（可选）
     */
    private Object data;
    
    /**
     * 创建成功响应的便捷方法
     * 
     * @param message 成功消息
     * @param operation 操作类型
     * @return 成功响应对象
     */
    public static OperationResponse success(String message, String operation) {
        return OperationResponse.builder()
                .success(true)
                .message(message)
                .operation(operation)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建成功响应的便捷方法（带用户ID）
     * 
     * @param message 成功消息
     * @param operation 操作类型
     * @param userId 用户ID
     * @return 成功响应对象
     */
    public static OperationResponse success(String message, String operation, String userId) {
        return OperationResponse.builder()
                .success(true)
                .message(message)
                .operation(operation)
                .userId(userId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建失败响应的便捷方法
     * 
     * @param errorMessage 错误信息
     * @param operation 操作类型
     * @return 失败响应对象
     */
    public static OperationResponse error(String errorMessage, String operation) {
        return OperationResponse.builder()
                .success(false)
                .message(errorMessage)
                .operation(operation)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建失败响应的便捷方法（带用户ID）
     * 
     * @param errorMessage 错误信息
     * @param operation 操作类型
     * @param userId 用户ID
     * @return 失败响应对象
     */
    public static OperationResponse error(String errorMessage, String operation, String userId) {
        return OperationResponse.builder()
                .success(false)
                .message(errorMessage)
                .operation(operation)
                .userId(userId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}