package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息明细实体，对应表 ai_chat_message。
 * 关系与设计：
 * - 与 AiConversation 为多对一关系，通过 conversation_id 进行关联；允许为 null 表示临时消息（不推荐）。
 * - role 仅允许 user/assistant/system 三类，和 Spring AI 消息类型一一对应。
 * - status 约定：1=成功入库；9=错误（如下游模型失败或被客户端中断时存储错误元信息）。
 * - metadata 存储 JSON 字符串（错误原因、动作/情绪、RAG片段ID等），便于后续排查与分析。
 * - 排序约定：查询时优先按 created_at 倒序，二级按 id 倒序；近期列表再反转为升序拼接上下文。
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
