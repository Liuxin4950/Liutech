package chat.liuxin.liutech.model;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论表
 * @TableName comments
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comments")
public class Comments extends BaseEntity {
    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 评论者用户ID
     */
    private Long userId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 父评论ID（用于回复功能）
     */
    private Long parentId;

    // 关联查询字段
    /**
     * 评论用户信息
     */
    @TableField(exist = false)
    private Users user;

    /**
     * 子评论列表
     */
    @TableField(exist = false)
    private List<Comments> children;
}