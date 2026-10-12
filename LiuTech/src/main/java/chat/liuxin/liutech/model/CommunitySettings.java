package chat.liuxin.liutech.model;

import lombok.Data;
import java.util.Date;

/** 社区角色领域持久数据，与真人用户身份独立。 */
@Data
public class CommunitySettings {
    private Long id;
    private Boolean enabled;
    /** 默认保留原有接力行为；管理员可独立关闭 AI 触发 AI。 */
    private Boolean botRelayEnabled = true;
    private Integer botDailyCommentLimit;
    private Integer siteDailyCommentLimit;
    private Integer postDailyCommentLimit;
    private Integer botDailyTaskLimit;
    private Integer siteDailyTaskLimit;
    private Integer minDelaySeconds;
    private Integer maxDelaySeconds;
    private Integer cooldownSeconds;
    private Integer maxChainComments;
    private Long version;
}
