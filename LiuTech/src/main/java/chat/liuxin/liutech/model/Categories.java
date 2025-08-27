package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章分类表
 * @TableName categories
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("categories")
public class Categories extends BaseEntity {
    /**
     * 分类名
     */
    private String name;

    /**
     * 分类描述
     */
    private String description;

    /**
     * 该分类下的文章数量
     */
    @TableField(exist = false)
    private Integer postCount;
}