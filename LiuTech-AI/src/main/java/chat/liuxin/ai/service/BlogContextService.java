package chat.liuxin.ai.service;

import chat.liuxin.ai.client.BlogApiClient;
import chat.liuxin.ai.dto.AuthorProfileDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private volatile String cachedSiteProfilePrompt;
    private volatile long siteProfileCachedAt = 0L;
    private static final long SITE_PROFILE_TTL_MS = Duration.ofMinutes(10).toMillis();

    /**
     * 根据上下文构建增强的系统提示
     *
     * @param context 前端传递的上下文信息，包含page、postId等
     * @return 增强后的上下文提示
     */
    public String buildContextPrompt(Map<String, Object> context, String userMessage) {
        StringBuilder contextPrompt = new StringBuilder();
        String page = context == null ? null : asString(context.get("page"));

        if (shouldIncludeSiteProfile(page, userMessage)) {
            String siteProfilePrompt = getSiteProfilePrompt();
            if (!siteProfilePrompt.isBlank()) {
                contextPrompt.append("【博客基础信息】\n");
                contextPrompt.append(siteProfilePrompt);
            }
        }

        if (context == null || context.isEmpty()) {
            return contextPrompt.toString();
        }

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

        appendRecommendationContext(contextPrompt, context);

        return contextPrompt.toString();
    }

    private boolean shouldIncludeSiteProfile(String page, String userMessage) {
        if ("about".equals(page) || "home".equals(page)) {
            return true;
        }
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String normalized = userMessage.toLowerCase();
        return normalized.contains("作者")
                || normalized.contains("博主")
                || normalized.contains("个人")
                || normalized.contains("关于你")
                || normalized.contains("关于这个博客")
                || normalized.contains("博客")
                || normalized.contains("站点")
                || normalized.contains("liutech");
    }

    /**
     * 根据上下文构建增强的系统提示
     *
     * @param context 前端传递的上下文信息，包含page、postId等
     * @return 增强后的上下文提示
     */
    public String buildContextPrompt(Map<String, Object> context) {
        return buildContextPrompt(context, null);
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

    private String getSiteProfilePrompt() {
        long now = System.currentTimeMillis();
        if (cachedSiteProfilePrompt != null && now - siteProfileCachedAt < SITE_PROFILE_TTL_MS) {
            return cachedSiteProfilePrompt;
        }

        synchronized (this) {
            if (cachedSiteProfilePrompt != null && now - siteProfileCachedAt < SITE_PROFILE_TTL_MS) {
                return cachedSiteProfilePrompt;
            }
            AuthorProfileDTO profile = blogApiClient.getAuthorProfile();
            cachedSiteProfilePrompt = profile != null ? profile.toAiReadableFormat() : "";
            siteProfileCachedAt = now;
            return cachedSiteProfilePrompt;
        }
    }

    @SuppressWarnings("unchecked")
    private void appendRecommendationContext(StringBuilder contextPrompt, Map<String, Object> context) {
        Object recommendationsObj = context.get("recommendations");
        if (!(recommendationsObj instanceof List<?> recommendations) || recommendations.isEmpty()) {
            return;
        }

        for (int recommendationIndex = recommendations.size() - 1; recommendationIndex >= 0; recommendationIndex--) {
            Object item = recommendations.get(recommendationIndex);
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> recommendation = (Map<String, Object>) rawMap;
            String reason = asString(recommendation.get("reason"));
            String type = asString(recommendation.get("type"));
            Object postsObj = recommendation.get("posts");
            if (!(postsObj instanceof List<?> posts) || posts.isEmpty()) {
                continue;
            }

            StringBuilder section = new StringBuilder();
            section.append("- 推荐类型: ").append(type != null ? type : "unknown");
            if (reason != null) {
                section.append(" | 推荐理由: ").append(reason);
            }
            section.append("\n");

            int index = 1;
            for (Object postObj : posts) {
                if (!(postObj instanceof Map<?, ?> postMapRaw)) {
                    continue;
                }
                Map<String, Object> post = (Map<String, Object>) postMapRaw;
                section.append("  ").append(index++).append(". ")
                        .append("ID=").append(asString(post.get("id")))
                        .append(" | 标题=").append(defaultString(asString(post.get("title")), "未命名文章"));
                section.append("\n");
                if (index > 3) {
                    break;
                }
            }
            contextPrompt.append("\n\n【最近展示给用户的推荐内容】\n");
            contextPrompt.append("以下内容已经真实展示给用户。如果用户追问刚才推荐的文章，请基于这些推荐项继续回答。\n");
            contextPrompt.append(section.toString().trim());
            return;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultString(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
