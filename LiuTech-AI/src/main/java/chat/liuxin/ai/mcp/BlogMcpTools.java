package chat.liuxin.ai.mcp;

import chat.liuxin.ai.client.BlogApiClient;
import chat.liuxin.ai.dto.CategoryDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 博客工具类
 * 提供AI可调用的博客数据查询功能
 *
 * 核心功能：
 * 1. 搜索文章 - 根据关键词搜索
 * 2. 获取分类文章 - 根据分类ID获取文章列表
 * 3. 获取最新文章 - 获取最新发布的文章
 * 4. 获取热门文章 - 获取评论最多的文章
 * 5. 获取所有分类 - 获取所有分类列表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogMcpTools {

    private final BlogApiClient blogApiClient;

    /**
     * 根据关键词搜索博客文章
     *
     * @param keyword 搜索关键词
     * @param limit 返回数量，默认5
     * @return 文章列表
     */
    public List<PostSummaryDTO> searchPosts(String keyword, Integer limit) {
        log.debug("工具调用: searchPosts, keyword={}, limit={}", keyword, limit);
        List<PostSummaryDTO> results = blogApiClient.searchPosts(keyword, limit);
        log.debug("搜索结果: {} 篇", results.size());
        return results;
    }

    /**
     * 获取某个分类下的文章列表
     *
     * @param categoryId 分类ID
     * @param limit 返回数量，默认5
     * @return 文章列表
     */
    public List<PostSummaryDTO> getPostsByCategory(Long categoryId, Integer limit) {
        log.debug("工具调用: getPostsByCategory, categoryId={}, limit={}", categoryId, limit);
        int size = limit != null ? limit : 5;
        List<PostSummaryDTO> results = blogApiClient.getPostsByCategory(categoryId, size);
        log.debug("分类文章结果: {} 篇", results.size());
        return results;
    }

    /**
     * 获取最新发布的文章
     *
     * @param limit 返回数量，默认5
     * @return 文章列表
     */
    public List<PostSummaryDTO> getLatestPosts(Integer limit) {
        log.debug("工具调用: getLatestPosts, limit={}", limit);
        int size = limit != null ? limit : 5;
        List<PostSummaryDTO> results = blogApiClient.getLatestPosts(size);
        log.debug("最新文章结果: {} 篇", results.size());
        return results;
    }

    /**
     * 获取热门文章（按评论数排序）
     *
     * @param limit 返回数量，默认5
     * @return 文章列表
     */
    public List<PostSummaryDTO> getHotPosts(Integer limit) {
        log.debug("工具调用: getHotPosts, limit={}", limit);
        int size = limit != null ? limit : 5;
        List<PostSummaryDTO> results = blogApiClient.getHotPosts(size);
        log.debug("热门文章结果: {} 篇", results.size());
        return results;
    }

    /**
     * 获取所有分类列表
     *
     * @return 分类列表
     */
    public List<CategoryDTO> getAllCategories() {
        log.debug("工具调用: getAllCategories");
        List<CategoryDTO> results = blogApiClient.getAllCategories();
        log.debug("分类结果: {} 个", results.size());
        return results;
    }
}
