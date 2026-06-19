package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostDetailResp;
import chat.liuxin.liutech.resp.PostListResp;
import chat.liuxin.liutech.service.PostInteractionService;
import chat.liuxin.liutech.service.PostsService;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostsControllerTest {

    private PostsController controller;
    private PostsService postsService;
    private PostInteractionService postInteractionService;
    private UserUtils userUtils;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new PostsController();
        postsService = mock(PostsService.class);
        postInteractionService = mock(PostInteractionService.class);
        userUtils = mock(UserUtils.class);
        request = new MockHttpServletRequest();

        ReflectionTestUtils.setField(controller, "postsService", postsService);
        ReflectionTestUtils.setField(controller, "postInteractionService", postInteractionService);
        ReflectionTestUtils.setField(controller, "userUtils", userUtils);
    }

    // ========== getPostList ==========

    @Test
    void getPostList_shouldReturnPageResult() {
        PageResp<PostListResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(userUtils.getCurrentUserId()).thenReturn(null);
        when(postsService.getPostList(any(), isNull())).thenReturn(pageResp);

        Result<PageResp<PostListResp>> result = controller.getPostList(1, 10, null, null, null, "latest", request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0L, result.getData().getTotal());
    }

    @Test
    void getPostList_shouldPassParametersToService() {
        PageResp<PostListResp> pageResp = new PageResp<>(Collections.emptyList(), 5L, 2L, 5L);
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsService.getPostList(any(), eq(1L))).thenReturn(pageResp);

        Result<PageResp<PostListResp>> result = controller.getPostList(2, 5, 10L, 3L, "test", "hot", request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(postsService).getPostList(argThat(req ->
                req.getPage() == 2
                        && req.getSize() == 5
                        && req.getCategoryId().equals(10L)
                        && req.getTagId().equals(3L)
                        && "test".equals(req.getKeyword())
                        && "hot".equals(req.getSort())
                        && "published".equals(req.getStatus())
        ), eq(1L));
    }

    // ========== getPostDetail ==========

    @Test
    void getPostDetail_shouldReturnPostWhenExists() {
        PostDetailResp detail = new PostDetailResp();
        detail.setTitle("Test Post");
        when(userUtils.getCurrentUserId()).thenReturn(null);
        when(postsService.getPostDetail(1L, null)).thenReturn(detail);

        Result<PostDetailResp> result = controller.getPostDetail(1L, request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Test Post", result.getData().getTitle());
    }

    @Test
    void getPostDetail_shouldReturnErrorWhenNotFound() {
        when(userUtils.getCurrentUserId()).thenReturn(null);
        when(postsService.getPostDetail(999L, null)).thenReturn(null);

        Result<PostDetailResp> result = controller.getPostDetail(999L, request);

        assertEquals(ErrorCode.ARTICLE_NOT_FOUND.getCode(), result.getCode());
        assertNull(result.getData());
    }

    // ========== getHotPosts ==========

    @Test
    void getHotPosts_shouldReturnList() {
        PostListResp post = new PostListResp();
        post.setTitle("Hot Post");
        when(postsService.getHotPosts(5)).thenReturn(List.of(post));

        Result<List<PostListResp>> result = controller.getHotPosts(5);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("Hot Post", result.getData().get(0).getTitle());
    }

    @Test
    void getHotPosts_shouldReturnEmptyListWhenNoPosts() {
        when(postsService.getHotPosts(10)).thenReturn(Collections.emptyList());

        Result<List<PostListResp>> result = controller.getHotPosts(10);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== getLatestPosts ==========

    @Test
    void getLatestPosts_shouldReturnList() {
        PostListResp post = new PostListResp();
        post.setTitle("Latest Post");
        when(postsService.getLatestPosts(3)).thenReturn(List.of(post));

        Result<List<PostListResp>> result = controller.getLatestPosts(3);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void getLatestPosts_shouldUseDefaultLimit() {
        when(postsService.getLatestPosts(10)).thenReturn(Collections.emptyList());

        Result<List<PostListResp>> result = controller.getLatestPosts(10);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(postsService).getLatestPosts(10);
    }

    // ========== searchPosts ==========

    @Test
    void searchPosts_shouldReturnSearchResults() {
        PageResp<PostListResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(postsService.getPostList(any())).thenReturn(pageResp);

        Result<PageResp<PostListResp>> result = controller.searchPosts("keyword", 1, 10);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(postsService).getPostList(argThat(req ->
                "keyword".equals(req.getKeyword())
                        && "published".equals(req.getStatus())
        ));
    }

    @Test
    void searchPosts_shouldPassPaginationParams() {
        PageResp<PostListResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 2L, 5L);
        when(postsService.getPostList(any())).thenReturn(pageResp);

        Result<PageResp<PostListResp>> result = controller.searchPosts("test", 2, 5);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(postsService).getPostList(argThat(req ->
                req.getPage() == 2 && req.getSize() == 5
        ));
    }

    // ========== toggleLike ==========

    @Test
    void toggleLike_shouldReturnSuccessWhenLiked() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postInteractionService.toggleLike(1L, 1L)).thenReturn(true);

        Result<String> result = controller.toggleLike(1L, request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("liked", result.getData());
    }

    @Test
    void toggleLike_shouldReturnSuccessWhenUnliked() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postInteractionService.toggleLike(1L, 1L)).thenReturn(false);

        Result<String> result = controller.toggleLike(1L, request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("unliked", result.getData());
    }

    @Test
    void toggleLike_shouldFailWhenNotLoggedIn() {
        when(userUtils.getCurrentUserId()).thenReturn(null);

        Result<String> result = controller.toggleLike(1L, request);

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
    }

    @Test
    void toggleLike_shouldFailWhenServiceThrows() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postInteractionService.toggleLike(1L, 1L)).thenThrow(new RuntimeException("error"));

        Result<String> result = controller.toggleLike(1L, request);

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
    }

    // ========== toggleFavorite ==========

    @Test
    void toggleFavorite_shouldReturnSuccessWhenFavorited() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postInteractionService.toggleFavorite(1L, 1L)).thenReturn(true);

        Result<String> result = controller.toggleFavorite(1L, request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("favorited", result.getData());
    }

    @Test
    void toggleFavorite_shouldFailWhenNotLoggedIn() {
        when(userUtils.getCurrentUserId()).thenReturn(null);

        Result<String> result = controller.toggleFavorite(1L, request);

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
    }

    // ========== getDrafts ==========

    @Test
    void getDrafts_shouldReturnDraftsWhenLoggedIn() {
        PageResp<PostListResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsService.getPostList(any())).thenReturn(pageResp);

        Result<PageResp<PostListResp>> result = controller.getDrafts(1, 10, null, request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(postsService).getPostList(argThat(req ->
                "draft".equals(req.getStatus())
                        && req.getAuthorId().equals(1L)
        ));
    }

    @Test
    void getDrafts_shouldFailWhenNotLoggedIn() {
        when(userUtils.getCurrentUserId()).thenReturn(null);

        Result<PageResp<PostListResp>> result = controller.getDrafts(1, 10, null, request);

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
    }

    // ========== getMyPosts ==========

    @Test
    void getMyPosts_shouldReturnPostsWhenLoggedIn() {
        PageResp<PostListResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsService.getPostList(any(), eq(1L))).thenReturn(pageResp);

        Result<PageResp<PostListResp>> result = controller.getMyPosts(1, 10, null, request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void getMyPosts_shouldFailWhenNotLoggedIn() {
        when(userUtils.getCurrentUserId()).thenReturn(null);

        Result<PageResp<PostListResp>> result = controller.getMyPosts(1, 10, null, request);

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
    }

    // ========== getFavoritePosts ==========

    @Test
    void getFavoritePosts_shouldReturnFavoritesWhenLoggedIn() {
        PageResp<PostListResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postInteractionService.getFavoritePosts(any(), eq(1L))).thenReturn(pageResp);

        Result<PageResp<PostListResp>> result = controller.getFavoritePosts(1, 10, null, request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void getFavoritePosts_shouldFailWhenNotLoggedIn() {
        when(userUtils.getCurrentUserId()).thenReturn(null);

        Result<PageResp<PostListResp>> result = controller.getFavoritePosts(1, 10, null, request);

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
    }
}
