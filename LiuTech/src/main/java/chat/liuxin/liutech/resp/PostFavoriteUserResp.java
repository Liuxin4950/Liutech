package chat.liuxin.liutech.resp;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章收藏用户响应类（管理端查看某篇文章被哪些用户收藏）
 *
 * @author 刘鑫
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostFavoriteUserResp {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 收藏时间（取最近一次收藏/取消的更新时间，取消收藏的记录已被过滤）
     */
    private Date favoriteTime;
}
