package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.application.ArticleResultMapper;
import chat.liuxin.ai.common.client.BlogApiClient;
import chat.liuxin.ai.dto.PostSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicArticleToolTest {

    @Test
    void shouldReuseCachedLatestArticleResults() {
        BlogApiClient blogApiClient = mock(BlogApiClient.class);
        PublicArticleTool tool = new PublicArticleTool(blogApiClient, new ArticleResultMapper());

        PostSummaryDTO post = new PostSummaryDTO();
        post.setId(1L);
        post.setTitle("缓存测试");
        when(blogApiClient.getLatestPosts(5)).thenReturn(List.of(post));

        assertEquals(1, tool.latestArticles(5).size());
        assertEquals(1, tool.latestArticles(5).size());

        verify(blogApiClient, times(1)).getLatestPosts(5);
    }
}
