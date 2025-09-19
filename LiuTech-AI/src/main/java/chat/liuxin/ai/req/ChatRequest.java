package chat.liuxin.ai.req;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

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
}