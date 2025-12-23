package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息明细实体，对应表 ai_chat_message。
 * 作者：刘鑫
 * 时间：2025-12-22
 * 版本：v2.0
 *
 * 关系与设计：
 * - 与 AiConversation 为多对一关系，通过 conversation_id 进行关联。
 * - role 仅允许 user/assistant/system 三类，和 Spring AI 消息类型一一对应。
 * - status 约定：1=完成；2=部分；9=错误。
 * - tokens 字段记录Token总数（输入+输出），用于成本统计。
 * - seq_no 字段记录消息序号（同一会话内顺序），保证消息顺序。
 * - metadata 使用JSON格式存储扩展信息（如温度、traceId、错误信息等）。
 * - 排序约定：查询时优先按 created_at 倒序，二级按 id 倒序；近期列表再反转为升序拼接上下文。
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("user_id")
    private String userId;

    /** 角色：user/assistant/system */
    @TableField("role")
    private String role;

    /** 文本内容：允许为null（如错误时AI内容为空） */
    @TableField("content")
    private String content;

    /** 消息序号（同一会话内顺序） */
    @TableField("seq_no")
    private Integer seqNo;

    /** 模型名称：便于回溯（如 ollama:llama3.1 等） */
    @TableField("model")
    private String model;

    /** Token总数（输入+输出） */
    @TableField("tokens")
    private Integer tokens;

    /** 扩展元数据（JSON格式）：如温度、traceId、错误信息等 */
    @TableField("metadata")
    private String metadata;

    /** 状态：1=完成；2=部分；9=错误 */
    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
