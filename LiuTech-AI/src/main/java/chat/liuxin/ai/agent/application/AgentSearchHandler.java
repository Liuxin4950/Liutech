package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.agent.response.ArticleResultItem;
import chat.liuxin.ai.agent.response.ArticleResultsPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 搜索文章 Handler。
 *
 * <p>根据用户关键词搜索公开文章，返回匹配结果列表。
 * 纯确定性工具调用，不调用 AI 模型生成文本。
 *
 * @author liuxin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSearchHandler implements AgentIntentHandler {

    private final PublicArticleTool publicArticleTool;

    /** 常见技术主题关键词 */
    private static final String[] ARTICLE_TOPICS = {
            "spring boot", "spring ai", "typescript", "javascript", "docker", "kubernetes",
            "nginx", "mysql", "redis", "java", "vue", "react", "jwt", "agent", "ai", "vite", "maven"
    };

    @Override
    public AgentChatResponse handle(AgentChatRequest request, AgentHandlerContext ctx) {
        String keyword = extractArticleKeyword(request.getMessage());
        log.info("搜索文章: keyword={}, taskId={}", keyword, ctx.getTaskId());

        // 调用工具搜索文章
        long start = System.currentTimeMillis();
        List<ArticleResultItem> items = publicArticleTool.searchArticles(keyword, 6);
        long duration = System.currentTimeMillis() - start;
        log.info("搜索完成: keyword={}, results={}, duration={}ms", keyword, items.size(), duration);

        if (items == null) items = List.of();

        // 构建响应
        ArticleResultsPayload payload = ArticleResultsPayload.builder()
                .source("search")
                .query(keyword)
                .reason(items.isEmpty() ? "没有找到匹配文章" : "我找到了一些可以继续阅读的文章")
                .items(items)
                .build();

        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .conversationId(ctx.getConversationId())
                .handlerName("search")
                .message(items.isEmpty()
                        ? "我暂时没有找到匹配的文章，可以换个关键词试试。"
                        : "我找到了这些相关文章，可以直接点开阅读。")
                .articleResults(payload)
                .build();
    }

    /**
     * 从用户消息中提取搜索关键词。
     */
    private String extractArticleKeyword(String message) {
        if (message == null || message.isBlank()) return "";
        String normalized = message.toLowerCase();

        // 检查是否包含主题关键词
        for (String topic : ARTICLE_TOPICS) {
            if (normalized.contains(topic)) return topic;
        }

        // 移除停用词
        String[] stopwords = {"相关文章", "类似文章", "推荐", "搜索", "查找", "找一下", "找找",
                "文章", "博客", "教程", "内容", "我在", "我想", "正在", "学习", "了解",
                "关于", "相关", "有没有", "有", "你", "给我", "帮我", "几篇", "一些",
                "一下", "几个", "哪些", "什么", "有什么", "的", "吗", "呢", "啊"};
        String keyword = normalized.replaceAll("[，。！？、,.!?]", " ");
        for (String stopword : stopwords) {
            keyword = keyword.replace(stopword, "");
        }
        keyword = keyword.trim().replaceAll("\\s+", " ");
        return keyword.length() > 40 ? keyword.substring(0, 40).trim() : keyword;
    }
}



