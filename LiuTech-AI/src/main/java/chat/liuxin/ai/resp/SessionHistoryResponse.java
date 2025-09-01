package chat.liuxin.ai.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 会话历史记录响应类
 * 
 * @author 刘鑫
 * @since 2025-09-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionHistoryResponse {
    
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
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 历史消息列表
     */
    private List<MessageInfo> messages;
    
    /**
     * 消息总数
     */
    private int totalCount;
    
    /**
     * 消息信息内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageInfo {
        
        /**
         * 消息类型（user/assistant）
         */
        private String type;
        
        /**
         * 消息内容
         */
        private String content;
        
        /**
         * 消息时间戳
         */
        private Long timestamp;
        
        /**
         * 从Spring AI Message转换
         */
        public static MessageInfo fromMessage(Message message) {
            MessageInfo info = new MessageInfo();
            info.setType(message.getMessageType().getValue());
            info.setContent(message.getContent());
            info.setTimestamp(System.currentTimeMillis());
            return info;
        }
    }
    
    /**
     * 创建成功响应
     */
    public static SessionHistoryResponse success(String userId, String sessionId, List<MessageInfo> messages) {
        return new SessionHistoryResponse(true, null, userId, sessionId, messages, messages.size());
    }
    
    /**
     * 创建失败响应
     */
    public static SessionHistoryResponse error(String message) {
        return new SessionHistoryResponse(false, message, null, null, null, 0);
    }
}