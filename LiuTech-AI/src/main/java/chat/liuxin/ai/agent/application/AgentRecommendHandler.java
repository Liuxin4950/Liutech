package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.agent.response.ArticleResultItem;
import chat.liuxin.ai.agent.response.ArticleResultsPayload;
import chat.liuxin.ai.dto.PostDetailDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 推荐文章 Handler。
 *
 * <p>根据用户兴趣或当前浏览文章推荐相关文章。
 * 如果用户指定了主题，按主题搜索；否则返回最近更新。
 *
 * @author liuxin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRecommendHandler implements AgentIntentHandler {

    private final PublicArticleTool publicArticleTool;

    /** 常见技术主题关键词 */
    private static final String[] ARTICLE_TOPICS = {
            "spring boot", "spring ai", "typescript", "javascript", "docker", "kubernetes",
            "nginx", "mysql", "redis", "java", "vue", "react", "jwt", "agent", "ai", "vite", "maven"
    };

    @Override
    public AgentChatResponse handle(AgentChatRequest request, AgentHandlerContext ctx) {
        String keyword = extractArticleKeyword(request.getMessage());
        boolean topicRecommendation = !keyword.isBlank();
        PostDetailDTO currentArticle = null;

        // 如果用户正在看文章，尝试推荐相关文章
        if (!topicRecommendation && resolvePostId(request) != null) {
            currentArticle = resolveCurrentArticle(request);
            keyword = recommendationKeywordFromCurrentArticle(currentArticle);
            topicRecommendation = !keyword.isBlank();
        }

        // 搜索推荐文章
        String searchKeyword = keyword;
        long start = System.currentTimeMillis();
        List<ArticleResultItem> items = topicRecommendation
                ? publicArticleTool.searchArticles(searchKeyword, 5)
                : publicArticleTool.latestArticles(5);
        long duration = System.currentTimeMillis() - start;
        log.info("推荐文章: keyword={}, results={}, duration={}ms", searchKeyword, items.size(), duration);

        if (items == null) items = List.of();

        // 过滤掉当前文章
        if (currentArticle != null && currentArticle.getId() != null) {
            Long currentId = currentArticle.getId();
            items = items.stream()
                    .filter(item -> item.getId() == null || !item.getId().equals(currentId))
                    .toList();
        }

        // 按相关性排序
        if (topicRecommendation) {
            items = rankTopicRecommendationItems(keyword, items);
        }

        // 构建响应
        String source = currentArticle != null ? "related" : (topicRecommendation ? "search" : "latest");
        String reason = topicRecommendation
                ? (items.isEmpty() ? "没有找到与「" + keyword + "」直接相关的文章" : "与「" + keyword + "」相关的文章")
                : "先给你几篇最近更新的内容";

        ArticleResultsPayload payload = ArticleResultsPayload.builder()
                .source(source)
                .query(topicRecommendation ? keyword : request.getMessage())
                .reason(reason)
                .items(items)
                .build();

        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .conversationId(ctx.getConversationId())
                .handlerName("recommend")
                .message(recommendationMessage(topicRecommendation, keyword, items))
                .articleResults(payload)
                .build();
    }

    // ===== 文章解析 =====

    private Long resolvePostId(AgentChatRequest request) {
        if (request.getDraft() != null && request.getDraft().getPostId() != null) {
            return request.getDraft().getPostId();
        }
        if (request.getContext() != null) {
            Object postId = request.getContext().get("postId");
            if (postId instanceof Number n) return n.longValue();
            if (postId instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignore) {}
            }
        }
        return null;
    }

    private PostDetailDTO resolveCurrentArticle(AgentChatRequest request) {
        Long postId = resolvePostId(request);
        if (postId == null) return null;
        return publicArticleTool.getArticleDetail(postId);
    }

    private String recommendationKeywordFromCurrentArticle(PostDetailDTO article) {
        if (article == null) return "";
        if (article.getTags() != null) {
            for (String tag : article.getTags()) {
                if (tag != null && !tag.isBlank()) return tag.trim();
            }
        }
        if (article.getCategoryName() != null && !article.getCategoryName().isBlank()) {
            return article.getCategoryName().trim();
        }
        return article.getTitle() != null ? article.getTitle().trim() : "";
    }

    // ===== 关键词提取 =====

    private String extractArticleKeyword(String message) {
        if (message == null || message.isBlank()) return "";
        String normalized = message.toLowerCase();

        // 检查是否包含主题关键词
        for (String topic : ARTICLE_TOPICS) {
            if (normalized.contains(topic)) return topic;
        }

        // 移除停用词
        String[] stopwords = {"相关文章", "类似文章", "推荐", "搜索", "查找", "文章", "博客", "教程", "内容",
                "我想", "学习", "了解", "有", "你", "给我", "帮我"};
        String keyword = normalized.replaceAll("[，。！？、,.!?]", " ");
        for (String sw : stopwords) {
            keyword = keyword.replace(sw, "");
        }
        keyword = keyword.trim().replaceAll("\\s+", " ");
        return keyword.length() > 40 ? keyword.substring(0, 40).trim() : keyword;
    }

    // ===== 相关性排序 =====

    private List<ArticleResultItem> rankTopicRecommendationItems(String keyword, List<ArticleResultItem> items) {
        if (keyword == null || keyword.isBlank() || items == null || items.isEmpty()) {
            return items == null ? List.of() : items;
        }

        List<ArticleResultItem> ranked = items.stream()
                .sorted((a, b) -> Integer.compare(relevanceScore(keyword, b), relevanceScore(keyword, a)))
                .toList();

        // 过滤低相关性结果
        boolean hasStrong = ranked.stream().anyMatch(item -> relevanceScore(keyword, item) >= 5);
        return ranked.stream()
                .filter(item -> !hasStrong || relevanceScore(keyword, item) >= 5)
                .peek(item -> item.setReason(recommendationReason(keyword, item)))
                .toList();
    }

    private int relevanceScore(String keyword, ArticleResultItem item) {
        String kw = keyword.toLowerCase();
        int score = 0;
        if (item.getTitle() != null && item.getTitle().toLowerCase().contains(kw)) score += 10;
        if (item.getCategoryName() != null && item.getCategoryName().toLowerCase().contains(kw)) score += 5;
        if (item.getTagNames() != null) {
            for (String tag : item.getTagNames()) {
                if (tag != null && tag.toLowerCase().contains(kw)) score += 8;
            }
        }
        if (item.getSummary() != null && item.getSummary().toLowerCase().contains(kw)) score += 2;
        return score;
    }

    private String recommendationReason(String keyword, ArticleResultItem item) {
        String kw = keyword.toLowerCase();
        if (item.getTitle() != null && item.getTitle().toLowerCase().contains(kw)) {
            return "标题包含「" + keyword + "」";
        }
        if (item.getTagNames() != null) {
            for (String tag : item.getTagNames()) {
                if (tag != null && tag.toLowerCase().contains(kw)) {
                    return "标签包含「" + tag + "」";
                }
            }
        }
        if (item.getCategoryName() != null && item.getCategoryName().toLowerCase().contains(kw)) {
            return "分类匹配「" + keyword + "」";
        }
        return "与「" + keyword + "」相关";
    }

    // ===== 响应消息 =====

    private String recommendationMessage(boolean topicRecommendation, String keyword, List<ArticleResultItem> items) {
        if (topicRecommendation) {
            return items.isEmpty()
                    ? "我没有找到和「" + keyword + "」直接相关的文章，先不拿不相干的内容糊弄你。可以换个关键词，我再帮你找。"
                    : "我按「" + keyword + "」帮你找了几篇相关内容，可以直接点开看。";
        }
        return items.isEmpty() ? "我暂时没有拿到可推荐的文章。" : "我先给你挑了几篇最近更新的文章。";
    }
}


