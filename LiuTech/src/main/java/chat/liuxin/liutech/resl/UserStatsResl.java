package chat.liuxin.liutech.resl;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户统计信息响应类
 * 
 * @author liuxin
 */
@Data
public class UserStatsResl {
    /**
     * 用户基本信息
     */
    private String username;
    private String email;
    private String avatarUrl;
    private String nickname;
    private String bio;
    private BigDecimal points;
    private Date lastLoginAt;
    private Date createdAt;
    
    /**
     * 统计信息
     */
    private Long commentCount;      // 评论数量
    private Long postCount;         // 文章数量
    private Long draftCount;        // 草稿数量
    private Long viewCount;         // 访问量（暂时设为0，后续可扩展）
    
    /**
     * 最近活动
     */
    private Date lastCommentAt;     // 最后评论时间
    private Date lastPostAt;        // 最后发文时间
}