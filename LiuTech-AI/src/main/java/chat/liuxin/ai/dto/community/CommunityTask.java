package chat.liuxin.ai.dto.community;

import lombok.Data;
import java.time.LocalDateTime;

/** 后台任务，独立于用户聊天会话。 */
@Data
public class CommunityTask {
    private String id;
    private Long eventId;
    private Long botId;
    private Long postId;
    private Long commentId;
    private String rootEventId;
    private String status;
    private Integer attempts;
    private Integer failures;
    private Long memoryEpoch;
    private String decisionJson;
    private String contextVersion;
    private String error;
    private LocalDateTime availableAt;
    private LocalDateTime leaseUntil;
    private LocalDateTime createdAt;
}
