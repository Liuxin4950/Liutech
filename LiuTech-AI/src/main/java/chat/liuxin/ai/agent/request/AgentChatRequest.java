package chat.liuxin.ai.agent.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class AgentChatRequest {
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容长度不能超过4000个字符")
    private String message;

    private Long conversationId;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> context;

    /**
     * 管理端文章编辑器当前表单快照。管理员 Agent 只能基于该快照生成建议或创建草稿。
     */
    @Valid
    private AdminArticleDraftRequest draft;
}
