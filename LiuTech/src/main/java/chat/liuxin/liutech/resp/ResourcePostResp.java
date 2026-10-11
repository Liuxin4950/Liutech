package chat.liuxin.liutech.resp;

import java.util.Date;
import lombok.Data;

/** 管理端资源的文章引用摘要，不暴露文章正文。 */
@Data
public class ResourcePostResp {
    private Long resourceId;
    private Long postId;
    private String title;
    private String status;
    private String authorUsername;
    private Date deletedAt;
}
