package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 文章分类表
 * @TableName categories
 */
@Data
@TableName("categories")
public class Categories {
    /**
     * 分类ID
     */
    private Long id;

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