package chat.liuxin.liutech.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 创建评论请求类
 */
@Data
public class CreateCommentReq {
    
    /**
     * 文章ID
     */
    @NotNull(message = "文章ID不能为空")
    private Long postId;
    
    /**
     * 评论内容
     */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容不能超过1000字符")
    private String content;
    
    /**
     * 父评论ID（可选，用于回复评论）
     */
    private Long parentId;

    /** 展示名称可改，提及绑定稳定角色 ID。 */
    @Size(max=2, message="一次最多提及两个角色")
    private java.util.List<@jakarta.validation.constraints.Positive Long> mentionedBotIds;
}