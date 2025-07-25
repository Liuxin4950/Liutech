package chat.liuxin.liutech.controller.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.model.Tags;
import chat.liuxin.liutech.resl.PostListResl;
import chat.liuxin.liutech.service.CategoriesService;
import chat.liuxin.liutech.service.CommentsService;
import chat.liuxin.liutech.service.PostsService;
import chat.liuxin.liutech.service.TagsService;
import lombok.extern.slf4j.Slf4j;

/**
 * 首页控制器
 * 提供博客首页相关的数据聚合接口
 * 
 * @author liuxin
 */
@Slf4j
@RestController
@RequestMapping("/")
public class HomeController {

    @Autowired
    private PostsService postsService;
    
    @Autowired
    private CategoriesService categoriesService;
    
    @Autowired
    private TagsService tagsService;
    
    @Autowired
    private CommentsService commentsService;

    /**
     * 首页欢迎信息
     * 
     * @return 欢迎信息
     */
    @GetMapping
    public Result<String> home() {
        log.info("访问首页");
        return Result.success("欢迎访问刘鑫的技术博客！", "欢迎访问刘鑫的技术博客！");
    }

    /**
     * 获取首页数据聚合
     * 包括最新文章、热门文章、分类列表、热门标签、最新评论等
     * 
     * @return 首页数据
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboardData() {
        log.info("获取首页数据聚合");
        
        Map<String, Object> data = new HashMap<>();
        
        try {
            // 最新文章（5篇）
            List<PostListResl> latestPosts = postsService.getLatestPosts(5);
            data.put("latestPosts", latestPosts);
            
            // 热门文章（5篇）
            List<PostListResl> hotPosts = postsService.getHotPosts(5);
            data.put("hotPosts", hotPosts);
            
            // 分类列表（包含文章数量）
            List<Categories> categories = categoriesService.getAllCategoriesWithPostCount();
            data.put("categories", categories);
            
            // 热门标签（10个）
            List<Tags> hotTags = tagsService.getHotTags(10);
            data.put("hotTags", hotTags);
            
            // 最新评论（5条）
            List<Comments> latestComments = commentsService.getLatestComments(5);
            data.put("latestComments", latestComments);
            
            log.info("首页数据聚合成功 - 最新文章: {}, 热门文章: {}, 分类: {}, 热门标签: {}, 最新评论: {}", 
                    latestPosts.size(), hotPosts.size(), categories.size(), hotTags.size(), latestComments.size());
            
            return Result.success("获取成功", data);
            
        } catch (Exception e) {
            log.error("获取首页数据失败", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR, "获取首页数据失败");
        }
    }

    /**
     * 获取网站统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        log.info("获取网站统计信息");
        
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 文章总数
            long postCount = postsService.count();
            stats.put("postCount", postCount);
            
            // 分类总数
            long categoryCount = categoriesService.count();
            stats.put("categoryCount", categoryCount);
            
            // 标签总数
            long tagCount = tagsService.count();
            stats.put("tagCount", tagCount);
            
            // 评论总数
            long commentCount = commentsService.count();
            stats.put("commentCount", commentCount);
            
            log.info("网站统计信息 - 文章: {}, 分类: {}, 标签: {}, 评论: {}", 
                    postCount, categoryCount, tagCount, commentCount);
            
            return Result.success("获取成功", stats);
            
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR, "获取统计信息失败");
        }
    }
}
