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
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

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