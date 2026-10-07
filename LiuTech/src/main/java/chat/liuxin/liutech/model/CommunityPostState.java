package chat.liuxin.liutech.model;

import lombok.Data;
import java.util.Date;

/** 社区角色领域持久数据，与真人用户身份独立。 */
@Data
public class CommunityPostState {
    private Long postId;
    private Boolean enabled;
    private Boolean firstPublicSeen;
    private Long version;
}
