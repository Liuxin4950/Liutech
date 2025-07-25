package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 标签表
 * @TableName tags
 */
@Data
@TableName("tags")
public class Tags {
    /**
     * 标签ID
     */
    private Long id;

    /**
     * 标签名
     */
    private String name;

    /**
     * 该标签下的文章数量
     */
    @TableField(exist = false)
    private Integer postCount;
}