package chat.liuxin.liutech.req;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 文章创建请求
 */
@Data
public class PostCreateReq {
    /**
     * 文章标题
     */
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 200, message = "文章标题长度不能超过200个字符")
    private String title;

    /**
     * 文章内容（Markdown）
     */
    @NotBlank(message = "文章内容不能为空")
    private String content;

    /**
     * 摘要
     */
    @Size(max = 500, message = "摘要长度不能超过500个字符")
    private String summary;

    /**
     * 分类ID
     */
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    /**
     * 标签ID列表
     */
    private List<Long> tagIds;

    /**
     * 文章状态（draft: 草稿, published: 已发布）
     */
    private String status = "draft";

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
    private Integer viewCount = 0;

    /**
     * 点赞次数
     */
    private Integer likeCount = 0;
}