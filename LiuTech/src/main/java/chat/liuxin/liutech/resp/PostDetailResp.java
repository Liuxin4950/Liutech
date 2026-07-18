package chat.liuxin.liutech.resp;

import java.util.Date;
import java.util.List;

import chat.liuxin.liutech.model.Posts;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章详情响应
 * 继承 Posts 实体类，只添加扩展字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostDetailResp extends Posts {

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
     * 所属系列信息
     */
    private SeriesInfo series;

    /**
     * 系列目录（同系列下的文章列表，按 series_sort 升序，当前篇标记 current=true）
     */
    private List<SeriesCatalogItem> seriesCatalog;

    /**
     * 评论数量
     */
    private Integer commentCount;

    /**
     * 当前用户点赞状态（0-未点赞，1-已点赞）
     */
    private Integer likeStatus;

    /**
     * 当前用户收藏状态（0-未收藏，1-已收藏）
     */
    private Integer favoriteStatus;

    /**
     * 附件列表（文章详情展示用）
     */
    private List<AttachmentInfo> attachments;

    @Data
    public static class CategoryInfo {
        private Long id;
        private String name;
        private String description;
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

    /**
     * 所属系列信息
     */
    @Data
    public static class SeriesInfo {
        private Long id;
        private String name;
        private String description;
        private String coverImage;
        /** 当前文章在系列内的排序 */
        private Integer sort;
        /** 系列内文章总数 */
        private Integer totalCount;
    }

    /**
     * 系列目录项（详情页系列导航用）
     */
    @Data
    public static class SeriesCatalogItem {
        private Long id;
        private String title;
        private Integer sort;
        /** 是否为当前文章 */
        private Boolean current;
    }

    /**
     * 附件信息
     * 作者：刘鑫；时间：2025-09-08
     */
    @Data
    public static class AttachmentInfo {
        /** 附件关联ID（post_attachments.id） */
        private Long attachmentId;
        /** 资源ID（resources.id） */
        private Long resourceId;
        /** 文件名（resources.name） */
        private String fileName;
        /** 文件访问URL（resources.file_url） */
        private String fileUrl;
        /** 外部链接（resources.external_link） */
        private String externalLink;
        /** 资源类型（resources.resource_type） */
        private String resourceType;
        /** 下载所需积分（resources.points_needed，可选） */
        private Integer pointsNeeded;
        /** 关联时间（post_attachments.created_at） */
        private Date createdTime;
        /** 是否已购买（免费资源默认为true） */
        private Boolean purchased;
        /** 购买后说明（resources.purchased_note） */
        private String purchasedNote;
    }
}
