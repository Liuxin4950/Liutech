package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

/**
 * Live2D 表情/动作提示事件。
 */
@Data
@Builder
public class AvatarCuePayload {
    private Integer seq;
    private Long conversationId;
    private String expression;
    private String motion;
    private Double intensity;
    private Integer durationMs;
    private String text;
}
