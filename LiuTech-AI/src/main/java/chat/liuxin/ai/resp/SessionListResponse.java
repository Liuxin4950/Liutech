package chat.liuxin.ai.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话列表响应类
 * 
 * @author 刘鑫
 * @since 2025-09-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionListResponse {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话列表
     */
    private List<SessionInfo> sessions;
    
    /**
     * 会话信息内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionInfo {
        
        /**
         * 会话ID
         */
        private String sessionId;
        
        /**
         * 会话名称（可选，用于显示）
         */
        private String sessionName;
        
        /**
         * 消息数量
         */
        private int messageCount;
        
        /**
         * 最后活跃时间
         */
        private Long lastActiveTime;
        
        /**
         * 最后一条消息预览
         */
        private String lastMessage;
    }
    
    /**
     * 创建成功响应
     */
    public static SessionListResponse success(String userId, List<SessionInfo> sessions) {
        return new SessionListResponse(true, null, userId, sessions);
    }
    
    /**
     * 创建失败响应
     */
    public static SessionListResponse error(String message) {
        return new SessionListResponse(false, message, null, null);
    }
}