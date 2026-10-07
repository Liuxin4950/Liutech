package chat.liuxin.liutech.model;

import lombok.Data;
import java.util.Date;

/** 社区角色领域持久数据，与真人用户身份独立。 */
@Data
public class CommunityKnowledge {
    private Long id;
    private Long botId;
    private String title;
    private String content;
    private Long version;
    private Date createdAt;
    private Date updatedAt;
}
