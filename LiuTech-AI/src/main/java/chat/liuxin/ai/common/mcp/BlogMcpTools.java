package chat.liuxin.ai.common.mcp;

import chat.liuxin.ai.common.client.BlogApiClient;
import chat.liuxin.ai.dto.AuthorProfileDTO;
import chat.liuxin.ai.dto.CategoryDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
    @Tool(description = "根据关键词搜索博客文章，适合用户要求找文章、推荐文章、查相关文章时调用。推荐文章时必须使用 [标题](/post/ID) 格式引用")
    public List<PostSummaryDTO> searchPosts(
            @ToolParam(description = "搜索关键词，例如 Spring AI、JWT、Vue3") String keyword,
            @ToolParam(description = "返回数量，建议 1 到 8") Integer limit
    ) {
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
    @Tool(description = "根据分类ID获取该分类下的文章列表，适合用户要求查看某个分类相关文章时调用")
    public List<PostSummaryDTO> getPostsByCategory(
            @ToolParam(description = "分类ID") Long categoryId,
            @ToolParam(description = "返回数量，建议 1 到 8") Integer limit
    ) {
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
    @Tool(description = "获取博客最新发布的文章列表，适合用户要求看看最近更新了什么时调用。推荐文章时必须使用 [标题](/post/ID) 格式引用")
    public List<PostSummaryDTO> getLatestPosts(@ToolParam(description = "返回数量，建议 1 到 8") Integer limit) {
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
    @Tool(description = "获取博客热门文章列表，适合用户要求看热门内容时调用。推荐文章时必须使用 [标题](/post/ID) 格式引用")
    public List<PostSummaryDTO> getHotPosts(@ToolParam(description = "返回数量，建议 1 到 8") Integer limit) {
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
    @Tool(description = "获取博客全部分类列表，适合用户询问博客有哪些分类时调用")
    public List<CategoryDTO> getAllCategories() {
        log.debug("工具调用: getAllCategories");
        List<CategoryDTO> results = blogApiClient.getAllCategories();
        log.debug("分类结果: {} 个", results.size());
        return results;
    }

    @Tool(description = "根据文章ID获取文章详情，适合用户追问某篇文章内容、摘要或细节时调用")
    public PostDetailDTO getPostDetail(@ToolParam(description = "文章ID") Long postId) {
        log.debug("工具调用: getPostDetail, postId={}", postId);
        return blogApiClient.getPostDetail(postId);
    }

    @Tool(description = "获取博客作者与站点基础信息，适合用户询问作者是谁、博客定位、站点概况时调用")
    public AuthorProfileDTO getAuthorProfile() {
        log.debug("工具调用: getAuthorProfile");
        return blogApiClient.getAuthorProfile();
    }
}
