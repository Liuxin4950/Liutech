package chat.liuxin.ai.common.mcp;

import chat.liuxin.ai.common.client.BlogApiClient;
import chat.liuxin.ai.dto.AuthorProfileDTO;
import chat.liuxin.ai.dto.CategoryDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

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
public class BlogMcpTools implements ToolGroup {

    /** 所有人可用（游客/用户/管理员） */
    @Override
    public Set<String> allowedRoles() {
        return Set.of("ADMIN", "USER", "GUEST");
    }

    private final BlogApiClient blogApiClient;
    private final ToolResultBudget toolResultBudget;

    /** 工具调用未传 limit 时的默认返回数 */
    private static final int DEFAULT_TOOL_LIMIT = 5;

    /**
     * AI 会在用户说"找文章 / 推荐一篇讲 XX 的 / 相关文章有哪些"时调用。
     *
     * 转发到 {@link BlogApiClient#searchPosts},内部走主后端 /posts/search 全文检索。
     * 返回摘要 DTO 已带 url 字段,LLM 应用 [标题](/post/ID) 格式回引。
     */
    @Tool(description = "根据关键词搜索博客文章，适合用户要求找文章、推荐文章、查相关文章时调用。推荐文章时必须使用 [标题](/post/ID) 格式引用")
    public List<PostSummaryDTO> searchPosts(
            @ToolParam(description = "搜索关键词，例如 Spring AI、JWT、Vue3") String keyword,
            @ToolParam(description = "返回数量，建议 1 到 8") Integer limit
    ) {
        log.debug("工具调用: searchPosts, keyword={}, limit={}", keyword, limit);
        if (keyword == null || keyword.length() > 200) {
            throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("搜索关键词不能超过200个字符");
        }
        List<PostSummaryDTO> results = blogApiClient.searchPosts(keyword, boundedLimit(limit));
        log.debug("搜索结果: {} 篇", results.size());
        return results;
    }

    /**
     * AI 会在用户明确说"XX 分类下有什么文章"时调用。
     *
     * 需要先通过 {@link #getAllCategories()} 拿到分类 ID,再用 ID 请求主后端 /posts?categoryId=...。
     */
    @Tool(description = "根据分类ID获取该分类下的文章列表，适合用户要求查看某个分类相关文章时调用")
    public List<PostSummaryDTO> getPostsByCategory(
            @ToolParam(description = "分类ID") Long categoryId,
            @ToolParam(description = "返回数量，建议 1 到 8") Integer limit
    ) {
        log.debug("工具调用: getPostsByCategory, categoryId={}, limit={}", categoryId, limit);
        int size = boundedLimit(limit);
        List<PostSummaryDTO> results = blogApiClient.getPostsByCategory(categoryId, size);
        log.debug("分类文章结果: {} 篇", results.size());
        return results;
    }

    /**
     * AI 会在用户问"最近更新了什么 / 有什么新文章"时调用,拉取主后端 /posts/latest。
     */
    @Tool(description = "获取博客最新发布的文章列表，适合用户要求看看最近更新了什么时调用。推荐文章时必须使用 [标题](/post/ID) 格式引用")
    public List<PostSummaryDTO> getLatestPosts(@ToolParam(description = "返回数量，建议 1 到 8") Integer limit) {
        log.debug("工具调用: getLatestPosts, limit={}", limit);
        int size = boundedLimit(limit);
        List<PostSummaryDTO> results = blogApiClient.getLatestPosts(size);
        log.debug("最新文章结果: {} 篇", results.size());
        return results;
    }

    /**
     * AI 会在用户问"最火的 / 热门文章"时调用,主后端 /posts/hot 按评论数排序。
     */
    @Tool(description = "获取博客热门文章列表，适合用户要求看热门内容时调用。推荐文章时必须使用 [标题](/post/ID) 格式引用")
    public List<PostSummaryDTO> getHotPosts(@ToolParam(description = "返回数量，建议 1 到 8") Integer limit) {
        log.debug("工具调用: getHotPosts, limit={}", limit);
        int size = boundedLimit(limit);
        List<PostSummaryDTO> results = blogApiClient.getHotPosts(size);
        log.debug("热门文章结果: {} 篇", results.size());
        return results;
    }

    /**
     * AI 会在用户问"博客都有哪些分类 / 你都写些什么"时调用,拉取主后端 /categories。
     * 也常作为 {@link #getPostsByCategory} 的前置,用于先查 ID。
     */
    @Tool(description = "获取博客全部分类列表，适合用户询问博客有哪些分类时调用")
    public List<CategoryDTO> getAllCategories() {
        log.debug("工具调用: getAllCategories");
        List<CategoryDTO> results = blogApiClient.getAllCategories();
        log.debug("分类结果: {} 个", results.size());
        return results;
    }

    /**
     * AI 会在用户追问"这篇文章讲了什么 / 详细内容"时调用,主后端 /posts/{id} 返回正文与元信息。
     */
    @Tool(description = "根据文章ID获取文章详情，适合用户追问某篇文章内容、摘要或细节时调用")
    public PostDetailDTO getPostDetail(@ToolParam(description = "文章ID") Long postId, ToolContext toolContext) {
        log.debug("工具调用: getPostDetail, postId={}", postId);
        // 正文可能上万字，按本次输入预算截断后再交给模型（整篇塞入会顶穿上下文）
        return toolResultBudget.truncateArticleContent(blogApiClient.getPostDetail(postId), toolContext);
    }

    @Tool(description = "分段读取文章正文。详情或当前页面资料不完整时，按字符偏移读取后续段落，不要假装已看过全文。")
    public String getPostSection(@ToolParam(description = "文章ID") Long postId,
                                 @ToolParam(description = "正文字符偏移，从0开始") Integer offset,
                                 ToolContext toolContext) {
        PostDetailDTO detail = blogApiClient.getPostDetail(postId);
        if (detail == null || detail.getContent() == null) return "文章不存在或正文不可读取";
        String content = detail.getContent();
        int start = Math.max(0, offset == null ? 0 : offset);
        if (start >= content.length()) return "已到正文结尾，总字符数=" + content.length();
        int size = Math.min(4000, toolResultBudget.resolveCharBudget(toolContext));
        int end = Math.min(content.length(), start + size);
        return "文章ID=" + postId + "，正文区间=" + start + ".." + end + "，总字符数=" + content.length()
                + "，下一段偏移=" + end + "\n" + content.substring(start, end);
    }

    /**
     * AI 会在用户问"作者是谁 / 你博主是谁 / 站点介绍"时调用,拉取主后端 /user/author/profile。
     */
    @Tool(description = "获取博客作者与站点基础信息，适合用户询问作者是谁、博客定位、站点概况时调用")
    public AuthorProfileDTO getAuthorProfile() {
        log.debug("工具调用: getAuthorProfile");
        return blogApiClient.getAuthorProfile();
    }

    private static int boundedLimit(Integer requested) {
        return requested == null ? DEFAULT_TOOL_LIMIT : Math.max(1, Math.min(8, requested));
    }
}
