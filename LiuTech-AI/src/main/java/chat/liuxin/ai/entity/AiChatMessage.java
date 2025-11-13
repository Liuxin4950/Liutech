package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息明细实体，对应表 ai_chat_message。
 * 设计说明：
 * - 不区分会话维度，按 userId 聚合一条时间线，便于后续做全局检索/摘要。
 * - role 仅允许 user/assistant/system 三类，和Spring AI消息类型一一对应。
 * - metadata 预留JSON字符串（如错误信息、补充上下文ID等）。
 * - createdAt/updatedAt 由数据库默认值与触发器自动维护。
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    /** 用户ID：当前阶段固定为 "0"，未来接入JWT后替换 */
    @TableField("user_id")
    private String userId;

    /** 角色：user/assistant/system */
    @TableField("role")
    private String role;

    /** 文本内容：允许为null（如错误时AI内容为空） */
    @TableField("content")
    private String content;

    /** 模型名称：便于回溯（如 ollama:llama3.1 等） */
    @TableField("model")
    private String model;

    /** 状态：1-成功；9-错误（与实现中保持一致） */
    @TableField("status")
    private Integer status;

    /** 预留JSON字符串，如错误栈、提示词ID、RAG片段ID列表等 */
    @TableField("metadata")
    private String metadata;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
