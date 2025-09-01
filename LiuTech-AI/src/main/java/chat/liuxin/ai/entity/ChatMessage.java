package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * AI聊天消息实体类
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_messages")
public class ChatMessage {

    /**
     * 消息ID（主键）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话标识符
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 消息角色
     */
    @TableField("role")
    private String role;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 使用的token数量
     */
    @TableField("tokens_used")
    private Integer tokensUsed;

    /**
     * 使用的AI模型名称
     */
    @TableField("model_name")
    private String modelName;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 消息角色枚举
     */
    public enum Role {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}