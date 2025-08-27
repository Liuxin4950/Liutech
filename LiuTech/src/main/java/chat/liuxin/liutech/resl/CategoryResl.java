package chat.liuxin.liutech.resl;

import lombok.Data;
import java.util.Date;

/**
 * 分类响应类
 * 用于管理端分类列表展示
 * 
 * @author 刘鑫
 */
@Data
public class CategoryResl {
    /**
     * 分类ID
     */
    private Long id;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类描述
     */
    private String description;

    /**
     * 该分类下的文章数量
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
}