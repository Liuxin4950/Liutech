package chat.liuxin.liutech.model;

import lombok.Data;
import java.util.Date;

/** 社区角色领域持久数据，与真人用户身份独立。 */
@Data
public class CommunityEvent {
    private Long id;
    private String eventType;
    private Long botId;
    private Long postId;
    private Long commentId;
    private String rootEventId;
    private String leaseToken;
    private Date availableAt;
    private Date createdAt;
}
