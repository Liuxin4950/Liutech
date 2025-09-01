package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * AI聊天会话实体类
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_sessions")
public class ChatSession {

    /**
     * 会话ID（主键）
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
     * 会话标题
     */
    @TableField("title")
    private String title;

    /**
     * 消息数量
     */
    @TableField("message_count")
    private Integer messageCount;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 软删除时间
     */
    @TableField("deleted_at")
    @TableLogic
    private LocalDateTime deletedAt;
}