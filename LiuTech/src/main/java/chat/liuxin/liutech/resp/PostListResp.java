package chat.liuxin.liutech.resp;

import java.util.Date;
import java.util.List;

import chat.liuxin.liutech.model.Posts;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章列表响应
 * 继承 Posts 实体类，只添加扩展字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostListResp extends Posts {

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
     * 当前用户点赞状态 (0-未点赞, 1-已点赞)
     */
    private Integer likeStatus;

    /**
     * 当前用户收藏状态 (0-未收藏, 1-已收藏)
     */
    private Integer favoriteStatus;

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
