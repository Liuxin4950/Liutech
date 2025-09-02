package chat.liuxin.liutech.resl;

import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 文章列表响应
 */
@Data
public class PostListResl {
    /**
     * 文章ID
     */
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 分类信息
     */
    private CategoryInfo category;

    /**
     * 作者信息
     */
    private AuthorInfo author;

    /**
     * 标签列表
     */
    private List<TagInfo> tags;

    /**
     * 评论数量
     */
    private Integer commentCount;

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
     * 当前用户点赞状态 (0-未点赞, 1-已点赞)
     */
    private Integer likeStatus;
    
    /**
     * 当前用户收藏状态 (0-未收藏, 1-已收藏)
     */
    private Integer favoriteStatus;

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
     * 删除时间（软删除）
     */
    private Date deletedAt;

    @Data
    public static class CategoryInfo {
        private Long id;
        private String name;
    }

    @Data
    public static class AuthorInfo {
        private Long id;
        private String username;
        private String avatarUrl;
    }

    @Data
    public static class TagInfo {
        private Long id;
        private String name;
    }
}