package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.CategoriesMapper;
import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.TagsMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.resp.DashboardResp;
import chat.liuxin.liutech.resp.DashboardResp.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 仪表盘统计服务
 * 提供管理端仪表盘所需的全部统计数据
 *
 * @author 刘鑫
 */
@Slf4j
@Service
public class DashboardService {

    @Autowired
    private PostsMapper postsMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CategoriesMapper categoriesMapper;

    @Autowired
    private TagsMapper tagsMapper;

    @Autowired
    private CommentsMapper commentsMapper;

    /**
     * 获取仪表盘全部统计数据
     *
     * @return 仪表盘统计数据
     */
    public DashboardResp getDashboardStats() {
        // 1. 基础统计
        BasicStats basicStats = getBasicStats();

        // 2. 文章状态分布
        List<StatusDistribution> statusDistribution = getPostStatusDistribution();

        // 3. 最近7天文章发布趋势
        List<TrendData> postTrend = getPostTrend(7);

        // 4. 最近7天用户注册趋势
        List<TrendData> userTrend = getUserTrend(7);

        // 5. 热门文章 TOP5
        List<PostRank> topPosts = getTopPosts(5);

        // 6. 活跃用户 TOP5
        List<UserRank> topAuthors = getTopAuthors(5);

        return DashboardResp.builder()
                .basicStats(basicStats)
                .postStatusDistribution(statusDistribution)
                .postTrend(postTrend)
                .userTrend(userTrend)
                .topPosts(topPosts)
                .topAuthors(topAuthors)
                .build();
    }

    /**
     * 获取基础统计数据
     */
    private BasicStats getBasicStats() {
        Integer totalPostsCount = postsMapper.countPostsForAdmin(null, null, null, null, false);
        Integer publishedPostsCount = postsMapper.countPublishedPosts();
        Long totalUsersCount = userMapper.countTotalUsers();
        Long totalCategoriesCount = categoriesMapper.selectCount(null);
        Long totalTagsCount = tagsMapper.selectCount(null);
        Integer totalCommentsCount = commentsMapper.countAllComments();
        Long totalViewsCount = postsMapper.countAllViews();

        long totalPosts = totalPostsCount != null ? totalPostsCount.longValue() : 0L;
        long publishedPosts = publishedPostsCount != null ? publishedPostsCount.longValue() : 0L;
        long draftPosts = Math.max(totalPosts - publishedPosts, 0L);
        long totalUsers = totalUsersCount != null ? totalUsersCount : 0L;
        long totalCategories = totalCategoriesCount != null ? totalCategoriesCount : 0L;
        long totalTags = totalTagsCount != null ? totalTagsCount : 0L;
        long totalComments = totalCommentsCount != null ? totalCommentsCount.longValue() : 0L;
        long totalViews = totalViewsCount != null ? totalViewsCount : 0L;

        return BasicStats.builder()
                .postCount(totalPosts)
                .publishedPostCount(publishedPosts)
                .draftPostCount(draftPosts)
                .userCount(totalUsers)
                .categoryCount(totalCategories)
                .tagCount(totalTags)
                .commentCount(totalComments)
                .totalViews(totalViews)
                .build();
    }

    /**
     * 获取文章状态分布
     */
    private List<StatusDistribution> getPostStatusDistribution() {
        List<StatusDistribution> distribution = new ArrayList<>();

        Integer publishedCount = postsMapper.countPublishedPosts();
        Integer draftCount = postsMapper.countPostsForAdmin(null, null, "draft", null, false);

        long published = publishedCount != null ? publishedCount.longValue() : 0L;
        long draft = draftCount != null ? draftCount.longValue() : 0L;
        long total = published + draft;

        if (total == 0) total = 1L; // 避免除零

        distribution.add(StatusDistribution.builder()
                .status("published")
                .displayName("已发布")
                .count(published)
                .percentage(Math.round(published * 100.0 / total * 100.0) / 100.0)
                .build());

        distribution.add(StatusDistribution.builder()
                .status("draft")
                .displayName("草稿")
                .count(draft)
                .percentage(Math.round(draft * 100.0 / total * 100.0) / 100.0)
                .build());

        return distribution;
    }

    /**
     * 获取最近N天的文章发布趋势
     */
    private List<TrendData> getPostTrend(int days) {
        List<TrendData> trend = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();

        for (int i = days - 1; i >= 0; i--) {
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.add(Calendar.DAY_OF_YEAR, -i);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date date = calendar.getTime();

            String dateStr = String.format("%04d-%02d-%02d",
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH));

            Integer count = postsMapper.countPostsByDate(date);
            long postCount = count != null ? count.longValue() : 0L;

            trend.add(TrendData.builder()
                    .date(dateStr)
                    .count(postCount)
                    .build());
        }

        return trend;
    }

    /**
     * 获取最近N天的用户注册趋势
     */
    private List<TrendData> getUserTrend(int days) {
        List<TrendData> trend = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();

        for (int i = days - 1; i >= 0; i--) {
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.add(Calendar.DAY_OF_YEAR, -i);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date date = calendar.getTime();

            String dateStr = String.format("%04d-%02d-%02d",
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH));

            Integer count = userMapper.countUsersByDate(date);
            long userCount = count != null ? count.longValue() : 0L;

            trend.add(TrendData.builder()
                    .date(dateStr)
                    .count(userCount)
                    .build());
        }

        return trend;
    }

    /**
     * 获取热门文章 TOP N
     */
    private List<PostRank> getTopPosts(int limit) {
        List<PostRank> topPosts = new ArrayList<>();

        var hotPosts = postsMapper.selectHotPostListResl(limit, null);
        if (hotPosts != null) {
            int rank = 1;
            for (var post : hotPosts) {
                topPosts.add(PostRank.builder()
                        .id(post.getId())
                        .title(post.getTitle())
                        .viewCount(post.getViewCount() != null ? post.getViewCount().longValue() : 0L)
                        .likeCount(post.getLikeCount() != null ? post.getLikeCount().longValue() : 0L)
                        .commentCount(post.getCommentCount() != null ? post.getCommentCount().longValue() : 0L)
                        .build());
                if (rank++ >= limit) break;
            }
        }

        return topPosts;
    }

    /**
     * 获取活跃用户 TOP N（按文章数量排序）
     */
    private List<UserRank> getTopAuthors(int limit) {
        List<UserRank> topAuthors = new ArrayList<>();

        var users = userMapper.selectUsersForAdmin(0, limit, null, null, null, null, false);
        if (users != null) {
            for (var user : users) {
                topAuthors.add(UserRank.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .postCount(user.getPostCount() != null ? user.getPostCount().longValue() : 0L)
                        .build());
            }
        }

        return topAuthors;
    }
}
