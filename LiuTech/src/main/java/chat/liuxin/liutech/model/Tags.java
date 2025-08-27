package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标签表
 * @TableName tags
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tags")
public class Tags extends BaseEntity {
    /**
     * 标签名
     */
    private String name;

    /**
     * 标签描述
     */
    private String description;

    /**
     * 该标签下的文章数量
     */
    @TableField(exist = false)
    private Integer postCount;
}