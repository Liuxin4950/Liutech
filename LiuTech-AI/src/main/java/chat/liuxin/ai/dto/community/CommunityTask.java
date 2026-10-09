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
    /** 列表展示事实，不入任务表；已生成任务优先使用该轮快照。 */
    private String postTitle;
    private String commentPreview;
    /** 管理端恢复操作说明，由当前任务事实推导，不入任务表。 */
    private String retryKind;
    private String retryReason;
    private Long publishedCommentId;
    private String rootEventId;
    private String status;
    /** 生成流程累计尝试，包含输入准备失败；实际模型轮数在运行 modelRounds。 */
    private Integer attempts;
    private Integer failures;
    private Long memoryEpoch;
    private String decisionJson;
    private String contextVersion;
    private String error;
    private LocalDateTime availableAt;
    private LocalDateTime leaseUntil;
    /** SQL 时钟计算，不根据 JDBC 映射后的日期推测调度；负数表示已到期。 */
    private Long dueSeconds;
    private Long leaseRemainingSeconds;
    private Long availableAtEpochMs;
    private Long createdAtEpochMs;
    private LocalDateTime createdAt;
}
