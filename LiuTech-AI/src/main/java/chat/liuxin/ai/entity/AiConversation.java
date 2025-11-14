package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体，对应表 ai_conversation。
 * 关系与设计：
 * - 会话（AiConversation）与消息（AiChatMessage）是一对多关系，消息通过 conversation_id 关联到会话。
 * - messageCount 与 lastMessageAt 在每次保存消息（用户/AI）时由服务层同步维护，便于列表显示与排序。
 * - 删除会话时采取“先删消息、再删会话”的顺序，避免残留孤儿消息。
 */
@Data
@TableName("ai_conversation")
public class AiConversation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private String userId;
    @TableField("type")
    private String type;
    @TableField("title")
    private String title;
    @TableField("status")
    private Integer status;
    /** 会话内消息总数（包含 user 与 assistant），由服务层维护 */
    @TableField("message_count")
    private Integer messageCount;
    /** 最近一条消息时间（用户或AI），用于按会话活跃度排序 */
    @TableField("last_message_at")
    private LocalDateTime lastMessageAt;
    /** 预留JSON字段，如会话标签、来源、扩展属性等 */
    @TableField("metadata")
    private String metadata;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
