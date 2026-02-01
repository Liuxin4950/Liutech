package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 留言表
 * @TableName messages
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("messages")
public class Messages extends BaseEntity {
    /**
     * 留言者昵称
     */
    private String nickname;

    /**
     * 留言者邮箱
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
     * 回复管理员ID
     */
    private Long repliedBy;
}
