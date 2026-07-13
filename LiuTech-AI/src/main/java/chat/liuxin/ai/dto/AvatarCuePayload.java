package chat.liuxin.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Live2D 表情/动作提示事件。
 *
 * @author 刘鑫
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
