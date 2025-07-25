package chat.liuxin.liutech.model;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 评论表
 * @TableName comments
 */
@Data
@TableName("comments")
public class Comments {
    /**
     * 评论ID
     */
    private Long id;

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
     * 父评论ID
     */
    private Long parentId;

    /**
     * 评论时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 软删除时间
     */
    private Date deletedAt;

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