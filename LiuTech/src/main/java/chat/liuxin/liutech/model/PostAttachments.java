package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章附件表（草稿态与文章态通用）
 * @TableName post_attachments
 * @author 刘鑫
 * @date 2025-01-08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("post_attachments")
public class PostAttachments extends BaseEntity {
    /**
     * 草稿关联键（未创建文章前使用）
     */
    private String draftKey;

    /**
     * 文章ID（创建文章后绑定）
     */
    private Long postId;

    /**
     * 资源ID（resources表主键）
     */
    private Long resourceId;

    /**
     * 附件类型（image, document, resource等）
     */
    private String type;
}