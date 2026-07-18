package chat.liuxin.liutech.resp;

import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 文章列表响应 DTO
 * 独立 DTO，不继承 Posts 实体，避免泄漏 createdBy/updatedBy/content 等敏感字段
 */
@Data
public class PostListResp {

    private Long id;
    private String title;
    private String summary;
    private String coverImage;
    private String thumbnail;
    private Long categoryId;
    private Long authorId;
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Date createdAt;
    private Date updatedAt;

    /** 分类信息 */
    private CategoryInfo category;

    /** 作者信息 */
    private AuthorInfo author;

    /** 标签列表 */
    private List<TagInfo> tags;

    /** 所属系列信息 */
    private SeriesInfo series;

    /** 评论数量 */
    private Integer commentCount;

    /** 当前用户点赞状态 (0-未点赞, 1-已点赞) */
    private Integer likeStatus;

    /** 当前用户收藏状态 (0-未收藏, 1-已收藏) */
    private Integer favoriteStatus;

    /** 删除时间（软删除，管理端使用） */
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

    @Data
    public static class SeriesInfo {
        private Long id;
        private String name;
        /** 当前文章在系列内的排序 */
        private Integer sort;
    }
}
