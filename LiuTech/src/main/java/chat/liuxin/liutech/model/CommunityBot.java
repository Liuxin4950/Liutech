package chat.liuxin.liutech.model;

import lombok.Data;
import java.util.Date;

/** 社区角色领域持久数据，与真人用户身份独立。 */
@Data
public class CommunityBot {
    private Long id;
    private String name;
    private String avatarUrl;
    private String personality;
    /** 管理员编写的角色行为指令，仍受社区执行权限约束。 */
    private String systemPrompt;
    private String background;
    private String interests;
    private Boolean enabled;
    private Integer participation;
    private Long version;
    private Date createdAt;
    private Date updatedAt;
    private Date deletedAt;
}
