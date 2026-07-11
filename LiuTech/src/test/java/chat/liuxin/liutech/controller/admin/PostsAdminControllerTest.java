package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.PostCreateReq;
import chat.liuxin.liutech.req.PostUpdateReq;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostCreateResp;
import chat.liuxin.liutech.resp.PostDetailResp;
import chat.liuxin.liutech.resp.PostListResp;
import chat.liuxin.liutech.service.PostsAdminService;
import chat.liuxin.liutech.service.PostsService;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostsAdminController 单元测试。
 *
 * 说明：类级 @PreAuthorize("hasRole('ADMIN')") 已保证认证，未认证请求在到达 Controller 前被
 * Spring Security 拦截（403）。因此本单元测试不覆盖"未认证"场景，那应由 @WebMvcTest 集成测试验证。
 * 异常处理由 GlobalExceptionHandler 统一兜底，service 抛异常时 Controller 直接传播，用 assertThrows 验证。
 */
class PostsAdminControllerTest {

    private PostsAdminController controller;
    private PostsService postsService;
    private PostsAdminService postsAdminService;
    private UserUtils userUtils;

    @BeforeEach
    void setUp() {
        postsService = mock(PostsService.class);
        postsAdminService = mock(PostsAdminService.class);
        userUtils = mock(UserUtils.class);

        controller = new PostsAdminController(postsService, postsAdminService, userUtils);
    }

    // ========== getPostList ==========

    @Test
    void getPostList_shouldReturnPageResult() {
        PageResp<PostListResp> pageResp = new PageResp<>(Collections.emptyList(), 0L, 1L, 10L);
        when(postsAdminService.getPostListForAdmin(1, 10, null, null, null, null, false)).thenReturn(pageResp);

        Result<PageResp<PostListResp>> result = controller.getPostList(1, 10, null, null, null, null, false);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(0L, result.getData().getTotal());
    }

    @Test
    void getPostList_shouldPassFilterParams() {
        PageResp<PostListResp> pageResp = new PageResp<>(Collections.emptyList(), 5L, 1L, 10L);
        when(postsAdminService.getPostListForAdmin(2, 5, "test", 10L, "published", 1L, true)).thenReturn(pageResp);

        Result<PageResp<PostListResp>> result = controller.getPostList(2, 5, "test", 10L, "published", 1L, true);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(postsAdminService).getPostListForAdmin(2, 5, "test", 10L, "published", 1L, true);
    }

    @Test
    void getPostList_shouldPropagateException() {
        when(postsAdminService.getPostListForAdmin(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("db error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.getPostList(1, 10, null, null, null, null, false));
    }

    // ========== getPostById ==========

    @Test
    void getPostById_shouldReturnPostWhenExists() {
        PostDetailResp detail = new PostDetailResp();
        detail.setTitle("Test Post");
        when(postsAdminService.getPostDetailForAdmin(1L)).thenReturn(detail);

        Result<PostDetailResp> result = controller.getPostById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Test Post", result.getData().getTitle());
    }

    @Test
    void getPostById_shouldReturnErrorWhenNotFound() {
        when(postsAdminService.getPostDetailForAdmin(999L)).thenReturn(null);

        Result<PostDetailResp> result = controller.getPostById(999L);

        assertEquals(ErrorCode.ARTICLE_NOT_FOUND.getCode(), result.getCode());
        assertNull(result.getData());
    }

    // ========== createPost ==========

    @Test
    void createPost_shouldReturnSuccess() {
        PostCreateReq req = new PostCreateReq();
        PostCreateResp resp = new PostCreateResp();
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsService.createPost(req, 1L)).thenReturn(resp);

        Result<PostCreateResp> result = controller.createPost(req);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void createPost_shouldPropagateException() {
        PostCreateReq req = new PostCreateReq();
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsService.createPost(any(), eq(1L))).thenThrow(new RuntimeException("error"));

        // 瘦身后 Controller 不再 try-catch，异常直接抛出由 GlobalExceptionHandler 统一兜底
        assertThrows(RuntimeException.class, () -> controller.createPost(req));
    }

    // ========== updatePost ==========

    @Test
    void updatePost_shouldReturnSuccess() {
        PostUpdateReq req = new PostUpdateReq();
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsService.updatePostForAdmin(any(), eq(1L))).thenReturn(true);

        Result<String> result = controller.updatePost(1L, req);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void updatePost_shouldReturnErrorWhenServiceFails() {
        PostUpdateReq req = new PostUpdateReq();
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsService.updatePostForAdmin(any(), eq(1L))).thenReturn(false);

        Result<String> result = controller.updatePost(1L, req);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== deletePost ==========

    @Test
    void deletePost_shouldReturnSuccess() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsAdminService.deletePostForAdmin(1L, 1L)).thenReturn(true);

        Result<String> result = controller.deletePost(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    // ========== batchDeletePosts ==========

    @Test
    void batchDeletePosts_shouldReturnSuccess() {
        when(postsAdminService.removeByIds(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchDeletePosts(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchDeletePosts_shouldReturnErrorWhenServiceFails() {
        when(postsAdminService.removeByIds(List.of(1L))).thenReturn(false);

        Result<String> result = controller.batchDeletePosts(List.of(1L));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== updatePostStatus ==========

    @Test
    void updatePostStatus_shouldReturnSuccess() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsAdminService.updatePostStatusForAdmin(1L, "published", 1L)).thenReturn(true);

        Result<String> result = controller.updatePostStatus(1L, "published");

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    // ========== batchUpdatePostStatus ==========

    @Test
    void batchUpdatePostStatus_shouldReturnSuccess() {
        when(postsAdminService.batchUpdateStatus(List.of(1L, 2L), "published")).thenReturn(true);

        Result<String> result = controller.batchUpdatePostStatus(List.of(1L, 2L), "published");

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchUpdatePostStatus_shouldReturnErrorWhenServiceFails() {
        when(postsAdminService.batchUpdateStatus(List.of(1L), "draft")).thenReturn(false);

        Result<String> result = controller.batchUpdatePostStatus(List.of(1L), "draft");

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== publishPost ==========

    @Test
    void publishPost_shouldReturnSuccess() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsAdminService.updatePostStatusForAdmin(1L, "published", 1L)).thenReturn(true);

        Result<String> result = controller.publishPost(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    // ========== offlinePost ==========

    @Test
    void offlinePost_shouldReturnSuccess() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(postsAdminService.updatePostStatusForAdmin(1L, "draft", 1L)).thenReturn(true);

        Result<String> result = controller.offlinePost(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    // ========== restorePost ==========

    @Test
    void restorePost_shouldReturnSuccess() {
        when(postsAdminService.restorePost(1L)).thenReturn(true);

        Result<String> result = controller.restorePost(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void restorePost_shouldReturnErrorWhenServiceFails() {
        when(postsAdminService.restorePost(1L)).thenReturn(false);

        Result<String> result = controller.restorePost(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchRestorePosts ==========

    @Test
    void batchRestorePosts_shouldReturnSuccess() {
        when(postsAdminService.batchRestorePosts(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchRestorePosts(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchRestorePosts_shouldReturnErrorWhenServiceFails() {
        when(postsAdminService.batchRestorePosts(List.of(1L))).thenReturn(false);

        Result<String> result = controller.batchRestorePosts(List.of(1L));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== permanentDeletePost ==========

    @Test
    void permanentDeletePost_shouldReturnSuccess() {
        when(postsAdminService.permanentDeletePost(1L)).thenReturn(true);

        Result<String> result = controller.permanentDeletePost(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void permanentDeletePost_shouldReturnErrorWhenServiceFails() {
        when(postsAdminService.permanentDeletePost(1L)).thenReturn(false);

        Result<String> result = controller.permanentDeletePost(1L);

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }

    // ========== batchPermanentDeletePosts ==========

    @Test
    void batchPermanentDeletePosts_shouldReturnSuccess() {
        when(postsAdminService.batchPermanentDeletePosts(List.of(1L, 2L))).thenReturn(true);

        Result<String> result = controller.batchPermanentDeletePosts(List.of(1L, 2L));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void batchPermanentDeletePosts_shouldReturnErrorWhenServiceFails() {
        when(postsAdminService.batchPermanentDeletePosts(List.of(1L))).thenReturn(false);

        Result<String> result = controller.batchPermanentDeletePosts(List.of(1L));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), result.getCode());
    }
}
