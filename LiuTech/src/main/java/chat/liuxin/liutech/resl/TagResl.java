package chat.liuxin.liutech.resl;

import lombok.Data;
import java.util.Date;

/**
 * 标签响应类
 * 用于管理端标签列表展示
 * 
 * @author 刘鑫
 */
@Data
public class TagResl {
    /**
     * 标签ID
     */
    private Long id;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签描述
     */
    private String description;

    /**
     * 该标签下的文章数量
     */
    private Integer postCount;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 创建者ID
     */
    private Long createdBy;

    /**
     * 创建者用户名
     */
    private String creatorUsername;

    /**
     * 删除时间（软删除）
     */
    private Date deletedAt;
}