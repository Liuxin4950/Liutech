package chat.liuxin.liutech.resp;

import java.util.Date;
import lombok.Data;

/**
 * 留言响应类
 */
@Data
public class MessageResp {
    /**
     * 留言ID
     */
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱（脱敏）
     */
    private String email;

    /**
     * 留言内容
     */
    private String content;

    /**
     * 状态(0待审核,1已审核,2已拒绝)
     */
    private Integer status;

    /**
     * 管理员回复
     */
    private String reply;

    /**
     * 回复时间
     */
    private Date repliedAt;

    /**
     * 留言时间
     */
    private Date createdAt;
}
