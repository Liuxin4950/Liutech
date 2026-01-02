package chat.liuxin.liutech.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 仪表盘统计数据响应类
 *
 * @author 刘鑫
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResp {

    /**
     * 基础统计数据
     */
    private BasicStats basicStats;

    /**
     * 文章状态分布
     */
    private List<StatusDistribution> postStatusDistribution;

    /**
     * 最近7天文章发布趋势
     */
    private List<TrendData> postTrend;

    /**
     * 最近7天用户注册趋势
     */
    private List<TrendData> userTrend;

    /**
     * 热门文章TOP5
     */
    private List<PostRank> topPosts;

    /**
     * 活跃用户TOP5
     */
    private List<UserRank> topAuthors;

    /**
     * 基础统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasicStats {
        /**
         * 文章总数
         */
        private Long postCount;

        /**
         * 已发布文章数
         */
        private Long publishedPostCount;

        /**
         * 草稿文章数
         */
        private Long draftPostCount;

        /**
         * 用户总数
         */
        private Long userCount;

        /**
         * 分类总数
         */
        private Long categoryCount;

        /**
         * 标签总数
         */
        private Long tagCount;

        /**
         * 评论总数
         */
        private Long commentCount;

        /**
         * 总浏览量
         */
        private Long totalViews;
    }

    /**
     * 状态分布
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusDistribution {
        /**
         * 状态名称
         */
        private String status;

        /**
         * 状态显示名称
         */
        private String displayName;

        /**
         * 数量
         */
        private Long count;

        /**
         * 占比百分比
         */
        private Double percentage;
    }

    /**
     * 趋势数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        /**
         * 日期（yyyy-MM-dd格式）
         */
        private String date;

        /**
         * 数量
         */
        private Long count;
    }

    /**
     * 文章排行榜
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostRank {
        /**
         * 文章ID
         */
        private Long id;

        /**
         * 文章标题
         */
        private String title;

        /**
         * 浏览量
         */
        private Long viewCount;

        /**
         * 点赞量
         */
        private Long likeCount;

        /**
         * 评论数
         */
        private Long commentCount;
    }

    /**
     * 用户排行榜
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRank {
        /**
         * 用户ID
         */
        private Long id;

        /**
         * 用户名
         */
        private String username;

        /**
         * 昵称
         */
        private String nickname;

        /**
         * 文章数量
         */
        private Long postCount;
    }
}
