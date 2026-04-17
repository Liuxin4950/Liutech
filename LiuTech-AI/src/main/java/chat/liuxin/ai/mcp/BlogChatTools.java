package chat.liuxin.ai.mcp;

import chat.liuxin.ai.client.BlogApiClient;
import chat.liuxin.ai.dto.AuthorProfileDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 聊天主链路只暴露高频工具，降低 schema 体积和模型选择负担。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogChatTools {

    private final BlogApiClient blogApiClient;

    @Tool(description = "根据关键词搜索博客文章，适合用户要求找文章、推荐文章、查相关文章时调用")
    public List<PostSummaryDTO> searchPosts(
            @ToolParam(description = "搜索关键词，例如 Spring AI、JWT、Vue3") String keyword,
            @ToolParam(description = "返回数量，建议 1 到 6") Integer limit
    ) {
        log.debug("工具调用: searchPosts, keyword={}, limit={}", keyword, limit);
        return blogApiClient.searchPosts(keyword, limit);
    }

    @Tool(description = "获取博客最新发布的文章列表，适合用户要求看看最近更新了什么时调用")
    public List<PostSummaryDTO> getLatestPosts(@ToolParam(description = "返回数量，建议 1 到 6") Integer limit) {
        log.debug("工具调用: getLatestPosts, limit={}", limit);
        return blogApiClient.getLatestPosts(limit != null ? limit : 5);
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
