package chat.liuxin.ai.service;

import chat.liuxin.ai.client.BlogApiClient;
import chat.liuxin.ai.dto.AuthorProfileDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlogContextServiceTest {

    private BlogApiClient blogApiClient;
    private BlogContextService blogContextService;

    @BeforeEach
    void setUp() {
        blogApiClient = mock(BlogApiClient.class);
        blogContextService = new BlogContextService(blogApiClient);
    }

    @Test
    void shouldIncludeAuthorProfileInBaseContext() {
        AuthorProfileDTO profile = new AuthorProfileDTO();
        profile.setName("刘鑫");
        profile.setTitle("全栈开发工程师");
        profile.setBio("这是我的个人博客");
        profile.setPosts(12L);
        profile.setComments(34L);
        profile.setViews(567L);
        when(blogApiClient.getAuthorProfile()).thenReturn(profile);

        String prompt = blogContextService.buildContextPrompt(Map.of("page", "home"));

        assertTrue(prompt.contains("【博客基础信息】"));
        assertTrue(prompt.contains("刘鑫"));
        assertTrue(prompt.contains("这是我的个人博客"));
    }

    @Test
    void shouldIncludeRenderedRecommendationsInContext() {
        when(blogApiClient.getAuthorProfile()).thenReturn(new AuthorProfileDTO());

        String prompt = blogContextService.buildContextPrompt(Map.of(
                "page", "home",
                "recommendations", List.of(
                        Map.of(
                                "type", "latest",
                                "reason", "最新发布的文章",
                                "posts", List.of(
                                        Map.of("id", 1, "title", "Spring AI 实战", "summary", "讲解博客中的 AI 能力", "categoryName", "AI"),
                                        Map.of("id", 2, "title", "Vue 组件拆分", "summary", "前端重构经验", "categoryName", "前端")
                                )
                        )
                )
        ));

        assertTrue(prompt.contains("【最近展示给用户的推荐内容】"));
        assertTrue(prompt.contains("Spring AI 实战"));
        assertTrue(prompt.contains("Vue 组件拆分"));
    }

    @Test
    void shouldIncludePostDetailWhenOnPostDetailPage() {
        when(blogApiClient.getAuthorProfile()).thenReturn(new AuthorProfileDTO());
        PostDetailDTO post = new PostDetailDTO();
        post.setTitle("文章标题");
        post.setAuthorName("刘鑫");
        post.setCategoryName("AI");
        post.setSummary("文章摘要");
        post.setContent("文章正文");
        post.setViewCount(10);
        post.setLikeCount(5);
        post.setCreatedAt("2026-04-17");
        when(blogApiClient.getPostDetail(123L)).thenReturn(post);

        String prompt = blogContextService.buildContextPrompt(Map.of("page", "post-detail", "postId", 123));

        assertTrue(prompt.contains("【当前页面上下文】"));
        assertTrue(prompt.contains("文章标题"));
        assertTrue(prompt.contains("文章正文"));
    }
}
