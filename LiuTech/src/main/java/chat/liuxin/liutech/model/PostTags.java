package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 文章-标签关联表
 * @TableName post_tags
 */
@Data
@TableName("post_tags")
public class PostTags {
    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 标签ID
     */
    private Long tagId;
}