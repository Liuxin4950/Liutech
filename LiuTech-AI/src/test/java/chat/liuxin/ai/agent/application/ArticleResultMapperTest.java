package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.dto.PostSummaryDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ArticleResultMapperTest {

    @Test
    void shouldCreateClickableArticleResultContract() {
        PostSummaryDTO post = new PostSummaryDTO();
        post.setId(12L);
        post.setTitle("Spring AI Agent 实战");
        post.setSummary("摘要");
        post.setCategoryName("后端");
        post.setTags(List.of("Java", "AI"));

        var item = new ArticleResultMapper().fromSummary(post, "search", "相关内容");

        assertNotNull(item);
        assertEquals(12L, item.getId());
        assertEquals("/post/12", item.getUrl());
        assertEquals("/admin/posts?postId=12", item.getAdminUrl());
        assertEquals(List.of("Java", "AI"), item.getTagNames());
        assertEquals("相关内容", item.getReason());
    }

    @Test
    void shouldCreateCurrentArticleCardFromDetail() {
        PostDetailDTO post = new PostDetailDTO();
        post.setId(18L);
        post.setTitle("Docker 容器化部署最佳实践");
        post.setSummary("Docker 摘要");
        post.setCategoryName("项目实战");
        post.setTags(List.of("Docker", "Nginx"));

        var item = new ArticleResultMapper().fromDetail(post, "current", "当前文章");

        assertNotNull(item);
        assertEquals(18L, item.getId());
        assertEquals("/post/18", item.getUrl());
        assertEquals("/admin/posts?postId=18", item.getAdminUrl());
        assertEquals("current", item.getSource());
        assertEquals(List.of("Docker", "Nginx"), item.getTagNames());
    }
}
