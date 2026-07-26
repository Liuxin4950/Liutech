package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章表
 * @TableName posts
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("posts")
public class Posts extends BaseEntity {
    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章内容（HTML，TinyMCE 富文本输出）
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
     * 封面图片URL
     */
    private String coverImage;

    /**
     * 缩略图URL
     */
    private String thumbnail;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 点赞数
     */
    private Integer likeCount;
    
    /**
     * 收藏数
     */
    private Integer favoriteCount;

    /**
     * 所属系列ID
     */
    private Long seriesId;

    /**
     * 系列内排序（升序，值越小越靠前）
     */
    private Integer seriesSort;
}