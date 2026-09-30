package chat.liuxin.liutech.service;

import chat.liuxin.liutech.resp.HomeDashboardResp;
import chat.liuxin.liutech.resp.SiteStatsResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 首页跨领域数据聚合；HTTP 封装留在 Controller。 */
@Service
@RequiredArgsConstructor
public class HomeService {
    private final PostsService postsService;
    private final CategoriesService categoriesService;
    private final TagsService tagsService;
    private final CommentsService commentsService;

    @Transactional(readOnly = true)
    public HomeDashboardResp getDashboardData() {
        return new HomeDashboardResp(postsService.getLatestPosts(5), postsService.getHotPosts(5),
                categoriesService.getAllCategoriesWithPostCount(), tagsService.getHotTags(10),
                commentsService.getLatestComments(5));
    }

    @Transactional(readOnly = true)
    public SiteStatsResp getStats() {
        return new SiteStatsResp(postsService.count(), categoriesService.count(), tagsService.count(), commentsService.count());
    }
}
