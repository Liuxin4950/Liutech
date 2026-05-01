package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.dto.PostSummaryDTO;
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
}
