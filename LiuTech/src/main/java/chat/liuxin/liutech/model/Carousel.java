package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 轮播图表
 * @TableName carousels
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("carousels")
public class Carousel extends BaseEntity {
    /**
     * 标题
     */
    private String title;

    /**
     * 图片URL
     */
    private String imageUrl;

    /**
     * 跳转链接
     */
    private String linkUrl;

    /**
     * 排序（越大越靠前）
     */
    private Integer sortOrder;

    /**
     * 状态(0禁用,1启用)
     */
    private Integer status;
}
