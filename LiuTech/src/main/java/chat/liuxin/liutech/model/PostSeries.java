package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章系列表
 * @TableName post_series
 *
 * @author 刘鑫
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("post_series")
public class PostSeries extends BaseEntity {

    /**
     * 系列名
     */
    private String name;

    /**
     * 系列描述
     */
    private String description;

    /**
     * 系列封面图URL
     */
    private String coverImage;

    /**
     * 系列内文章数量（非数据库字段，关联统计）
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer postCount;
}
