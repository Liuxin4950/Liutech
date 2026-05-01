package chat.liuxin.ai.agent.tool;

import chat.liuxin.ai.agent.application.ArticleResultMapper;
import chat.liuxin.ai.agent.response.ArticleResultItem;
import chat.liuxin.ai.client.BlogApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class PublicArticleTool {

    private final BlogApiClient blogApiClient;
    private final ArticleResultMapper articleResultMapper;
    private final Map<String, CacheEntry<List<ArticleResultItem>>> cache = new ConcurrentHashMap<>();

    @Value("${spring.ai.agent.tool-cache-ttl-ms:60000}")
    private long cacheTtlMs = 60000;

    @Value("${spring.ai.agent.max-article-results:8}")
    private int maxArticleResults = 8;

    public List<ArticleResultItem> searchArticles(String keyword, Integer limit) {
        int size = normalizeLimit(limit);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        return cached("search:" + normalizedKeyword + ":" + size, () -> articleResultMapper.fromSummaries(
                blogApiClient.searchPosts(normalizedKeyword, size),
                "search",
                normalizedKeyword.isBlank() ? "根据你的问题找到这些文章" : "与「" + normalizedKeyword + "」相关"));
    }

    public List<ArticleResultItem> latestArticles(Integer limit) {
        int size = normalizeLimit(limit);
        return cached("latest:" + size, () -> articleResultMapper.fromSummaries(
                blogApiClient.getLatestPosts(size),
                "latest",
                "最近发布的文章"));
    }

    public List<ArticleResultItem> hotArticles(Integer limit) {
        int size = normalizeLimit(limit);
        return cached("hot:" + size, () -> articleResultMapper.fromSummaries(
                blogApiClient.getHotPosts(size),
                "hot",
                "站内热度较高的文章"));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 5;
        }
        return Math.max(1, Math.min(limit, Math.max(1, maxArticleResults)));
    }

    private List<ArticleResultItem> cached(String key, Supplier<List<ArticleResultItem>> loader) {
        long now = System.currentTimeMillis();
        CacheEntry<List<ArticleResultItem>> entry = cache.get(key);
        if (entry != null && entry.expiresAt() > now) {
            return entry.value();
        }
        List<ArticleResultItem> value = List.copyOf(loader.get());
        cache.put(key, new CacheEntry<>(value, now + Math.max(0, cacheTtlMs)));
        return value;
    }

    private record CacheEntry<T>(T value, long expiresAt) {
    }
}
