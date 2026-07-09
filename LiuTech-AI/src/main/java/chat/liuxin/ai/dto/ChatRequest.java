package chat.liuxin.ai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.List;

/**
 * AI聊天请求类
 * 用于接收用户的聊天请求参数
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    /**
     * 用户消息内容
     * 必填字段，不能为空或空白字符
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容长度不能超过2000个字符")
    private String message;
    
    /**
     * 聊天模式（可选）
     * normal: 普通模式（默认）
     * stream: 流式模式
     */
    private String mode = "normal";
    
    /**
     * 模型名称（可选）
     * 如果不指定，使用系统默认模型
     */
    private String model;
    
    /**
     * 温度参数（可选）
     * 控制AI回复的随机性，范围0.0-1.0
     * 0.0表示最确定性，1.0表示最随机
     */
    private Double temperature;
    
    /**
     * 最大令牌数（可选）
     * 控制AI回复的最大长度
     */
    private Integer maxTokens;

    /**
     * 前端上下文（可选）
     * 例如：{"page":"article_detail","articleId":123,"user":"liuxin"}
     * 模型可据此决定 emotion/action，并在 metadata 中回传
     */
    private Map<String, Object> context;

    /**
     * 游客临时上下文（可选）
     * 仅用于匿名聊天，不允许服务端持久化。
     */
    @Valid
    private List<TempMessage> tempMessages;

    // 会话ID（可选）
    // 用于维护上下文，若不指定则创建新会话
    private Long conversationId;

    /**
     * 是否启用语音推理（可选）
     * - true：服务端会按分段规则触发 TTS 推理，并通过 SSE 推送 audio 事件
     * - false：只返回文本，不做语音推理
     */
    private Boolean ttsEnabled;

    /**
     * 管理员文章草稿快照（可选，仅写作助手使用）。
     * 编辑文章时由前端随请求发送，让 AI 能读取当前正在编辑的内容。
     */
    @Valid
    private AdminArticleDraftSnapshot draft;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TempMessage {
        @NotBlank(message = "临时消息角色不能为空")
        private String role;

        @NotBlank(message = "临时消息内容不能为空")
        @Size(max = 2000, message = "临时消息内容长度不能超过2000个字符")
        private String content;
    }
}
