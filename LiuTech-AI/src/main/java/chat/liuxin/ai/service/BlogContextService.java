package chat.liuxin.ai.service;

import chat.liuxin.ai.client.BlogApiClient;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 博客上下文服务
 * 根据前端传递的上下文信息，构建AI需要的博客内容
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogContextService {

    private final BlogApiClient blogApiClient;

    /**
     * 根据上下文构建增强的系统提示
     *
     * @param context 前端传递的上下文信息，包含page、postId等
     * @return 增强后的上下文提示
     */
    public String buildContextPrompt(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return "";
        }

        StringBuilder contextPrompt = new StringBuilder();
        String page = (String) context.get("page");

        // 如果在文章详情页，自动加载文章内容
        if ("post-detail".equals(page) && context.containsKey("postId")) {
            Object postIdObj = context.get("postId");
            Long postId = parsePostId(postIdObj);

            if (postId != null) {
                PostDetailDTO post = blogApiClient.getPostDetail(postId);
                if (post != null) {
                    contextPrompt.append("\n\n【当前页面上下文】\n");
                    contextPrompt.append("用户当前正在阅读以下文章：\n");
                    contextPrompt.append(post.toAiReadableFormat());
                    contextPrompt.append("\n\n你可以基于这篇文章回答用户的问题，帮助用户理解文章内容。");
                }
            }
        }

        return contextPrompt.toString();
    }

    /**
     * 执行文章搜索
     *
     * @param keyword 搜索关键词
     * @param limit 返回数量
     * @return 搜索结果的文本表示
     */
    public String searchPosts(String keyword, Integer limit) {
        List<PostSummaryDTO> results = blogApiClient.searchPosts(keyword, limit);

        if (results.isEmpty()) {
            return "没有找到与「" + keyword + "」相关的文章。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("搜索到 ").append(results.size()).append(" 篇相关文章：\n\n");

        for (int i = 0; i < results.size(); i++) {
            PostSummaryDTO post = results.get(i);
            sb.append(i + 1).append(". ");
            sb.append(post.toAiReadableFormat()).append("\n");
            if (post.getSummary() != null && !post.getSummary().isEmpty()) {
                sb.append("   摘要: ").append(post.getSummary()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 获取文章详情
     *
     * @param postId 文章ID
     * @return 文章详情的文本表示
     */
    public String getPostDetail(Long postId) {
        PostDetailDTO post = blogApiClient.getPostDetail(postId);

        if (post == null) {
            return "未找到ID为 " + postId + " 的文章。";
        }

        return post.toAiReadableFormat();
    }

    /**
     * 解析postId，支持多种类型
     */
    private Long parsePostId(Object postIdObj) {
        if (postIdObj == null) {
            return null;
        }

        try {
            if (postIdObj instanceof Number) {
                return ((Number) postIdObj).longValue();
            } else if (postIdObj instanceof String) {
                return Long.parseLong((String) postIdObj);
            }
        } catch (NumberFormatException e) {
            log.warn("无法解析postId: {}", postIdObj);
        }

        return null;
    }
}
