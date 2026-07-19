package chat.liuxin.liutech.vo;

import java.util.Date;

import lombok.Data;

/**
 * 文章附件查询 VO（Mapper 层关联查询返回）
 *
 * 对应 selectPostAttachmentsPublic 的结果集，替代原先的 Map<String,Object>，
 * 避免在 Service 层手动 map.get + 类型转换。downloadType 仅服务端判断付费用，不暴露前端。
 *
 * @author 刘鑫
 */
@Data
public class PostAttachmentVO {
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
    /** 购买后说明（resources.purchased_note） */
    private String purchasedNote;
    /** 下载类型（0-免费，1-积分），仅服务端判断付费用 */
    private Integer downloadType;
    /** 下载所需积分（resources.points_needed） */
    private Integer pointsNeeded;
    /** 关联时间（post_attachments.created_at） */
    private Date createdTime;
}
