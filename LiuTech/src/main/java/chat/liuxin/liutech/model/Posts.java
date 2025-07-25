package chat.liuxin.liutech.model;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 文章表
 * @TableName posts
 */
@Data
@TableName("posts")
public class Posts {
    /**
     * 文章ID
     */
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章内容（Markdown）
     */
    private String content;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 文章状态（draft: 草稿, published: 已发布, archived: 已归档）
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 软删除时间
     */
    private Date deletedAt;

    // 关联查询字段
    /**
     * 分类信息
     */
    @TableField(exist = false)
    private Categories category;

    /**
     * 作者信息
     */
    @TableField(exist = false)
    private Users author;

    /**
     * 标签列表
     */
    @TableField(exist = false)
    private List<Tags> tags;

    /**
     * 评论数量
     */
    @TableField(exist = false)
    private Integer commentCount;
}