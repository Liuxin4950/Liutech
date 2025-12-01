package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体，对应表 ai_conversation。
 * 作者：刘鑫
 * 时间：2025-12-01
 * 关系与设计：
 * - 会话（AiConversation）与消息（AiChatMessage）是一对多关系，消息通过 conversation_id 关联到会话。
 * - 简化设计，移除了冗余字段，保留核心功能。
 * - 删除会话时通过外键约束自动清理消息。
 */
@Data
@TableName("ai_conversation")
public class AiConversation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private String userId;
    @TableField("title")
    private String title;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
