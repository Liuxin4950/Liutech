package chat.liuxin.liutech.resp;

import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 评论响应类
 * 用于控制API返回的评论数据字段
 */
@Data
public class CommentResp {
    /**
     * 评论ID
     */
    private Long id;

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 父评论ID
     */
    private Long parentId;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 评论用户信息
     */
    private UserInfo user;

    /**
     * 子评论列表
     */
    private List<CommentResp> children;

    /**
     * 用户信息内部类
     */
    @Data
    public static class UserInfo {
        /**
         * 用户ID
         */
        private Long id;

        /**
         * 用户名
         */
        private String username;

        /**
         * 头像URL
         */
        private String avatarUrl;
    }
}
