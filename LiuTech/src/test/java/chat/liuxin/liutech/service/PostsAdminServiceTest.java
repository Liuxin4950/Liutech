package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.model.PostFavorites;
import chat.liuxin.liutech.model.PostLikes;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostListResp;
import chat.liuxin.liutech.utils.FileUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostsAdminService 单元测试
 * 覆盖管理端文章列表查询、文章删除等核心逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostsAdminServiceTest {

    @Mock
    private PostsMapper postsMapper;

    @Mock
    private PostTagsMapper postTagsMapper;

    @Mock
    private PostLikesMapper postLikesMapper;

    @Mock
    private PostFavoritesMapper postFavoritesMapper;

    @Mock
    private PostAttachmentsMapper postAttachmentsMapper;

    @Mock
    private CommentsMapper commentsMapper;

    @Mock
    private CommentsAdminService commentsAdminService;

    @Mock
    private FileUtil fileUtil;

    @Mock
    private ImagesService imagesService;

    @Mock
    private PostsService postsService;

    @Mock
    private CommunityService communityService;

    @InjectMocks
    private PostsAdminService postsAdminService;

    @BeforeEach
    void setUp() {
        // ServiceImpl.getById() 内部使用 baseMapper 字段，需要手动注入
        ReflectionTestUtils.setField(postsAdminService, "baseMapper", postsMapper);

        // 初始化 LambdaUpdateWrapper 需要的实体表元数据缓存
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PostLikes.class);
        TableInfoHelper.initTableInfo(assistant, PostFavorites.class);
        TableInfoHelper.initTableInfo(assistant, Posts.class);
    }

    @Test
    void batchPublishLocksAllArticlesBeforeUpdateAndCreatesCommunityEventsInOriginalTransaction() {
        Posts first=new Posts();first.setId(1L);
        Posts second=new Posts();second.setId(2L);
        when(postsMapper.selectByIdForUpdate(1L)).thenReturn(first);
        when(postsMapper.selectByIdForUpdate(2L)).thenReturn(second);
        when(postsMapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(2);
        assertTrue(postsAdminService.batchUpdateStatus(Arrays.asList(2L,1L,2L,9L),"published"));
        var order=inOrder(postsMapper,communityService);
        order.verify(postsMapper).selectByIdForUpdate(1L);
        order.verify(postsMapper).selectByIdForUpdate(2L);
        order.verify(postsMapper).selectByIdForUpdate(9L);
        order.verify(postsMapper).update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        order.verify(communityService).articleSaved(1L);
        order.verify(communityService).articleSaved(2L);
        verify(communityService,never()).articleSaved(9L);
    }

    @Test
    void failedBatchPublishDoesNotScheduleCommunityTasks() {
        Posts first=new Posts();first.setId(1L);
        when(postsMapper.selectByIdForUpdate(1L)).thenReturn(first);
        assertFalse(postsAdminService.batchUpdateStatus(Collections.singletonList(1L),"published"));
        verifyNoInteractions(communityService);
    }

    // ========== getPostListForAdmin 测试 ==========

    @Test
    void getPostListForAdmin_shouldReturnPaginatedResults() {
        PostListResp resp1 = new PostListResp();
        resp1.setId(1L);
        resp1.setTitle("Post A");

        PostListResp resp2 = new PostListResp();
        resp2.setId(2L);
        resp2.setTitle("Post B");

        Page<PostListResp> pageObj = new Page<>(1, 10);
        pageObj.setRecords(Arrays.asList(resp1, resp2));
        pageObj.setTotal(2);

        when(postsMapper.selectPostListForAdmin(any(Page.class), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(pageObj);

        PageResp<PostListResp> result = postsAdminService.getPostListForAdmin(1, 10, null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(2, result.getRecords().size());
        assertEquals(2L, result.getTotal());
        assertEquals("Post A", result.getRecords().get(0).getTitle());

        verify(postsMapper).selectPostListForAdmin(any(Page.class), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
        verify(postsService).fillTags(anyList());
    }

    @Test
    void getPostListForAdmin_shouldReturnEmptyPageWhenNoResults() {
        Page<PostListResp> pageObj = new Page<>(1, 10);
        pageObj.setRecords(Collections.emptyList());
        pageObj.setTotal(0);

        when(postsMapper.selectPostListForAdmin(any(Page.class), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(pageObj);

        PageResp<PostListResp> result = postsAdminService.getPostListForAdmin(1, 10, null, null, null, null, null, null);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0L, result.getTotal());

        verify(postsService).fillTags(anyList());
    }

    // ========== deletePostForAdmin 测试 ==========

    @Test
    void deletePostForAdmin_shouldDeleteSuccessfully() {
        Long postId = 1L;
        Long operatorId = 10L;

        Posts post = new Posts();
        post.setId(postId);
        post.setTitle("Test Post");

        when(postsMapper.selectActiveForUpdate(postId)).thenReturn(post);
        when(postsMapper.deleteById(eq(postId), any(), eq(operatorId))).thenReturn(1);

        boolean result = postsAdminService.deletePostForAdmin(postId, operatorId);

        assertTrue(result);

        verifyNoInteractions(postTagsMapper, postLikesMapper, postFavoritesMapper);
        verify(postsMapper).deleteById(eq(postId), any(), eq(operatorId));
    }

    @Test
    void deletePostForAdmin_shouldThrowWhenPostNotFound() {
        Long postId = 999L;
        Long operatorId = 10L;

        when(postsMapper.selectActiveForUpdate(postId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postsAdminService.deletePostForAdmin(postId, operatorId));

        assertEquals(ErrorCode.ARTICLE_NOT_FOUND.getCode(), ex.getCode());

        verify(postTagsMapper, never()).deleteByPostId(anyLong());
        verify(postsMapper, never()).deleteById(anyLong(), any(), anyLong());
    }

    @Test
    void deletePostForAdmin_shouldThrowWhenPostIsSoftDeleted() {
        Long postId = 1L;
        Long operatorId = 10L;

        Posts deletedPost = new Posts();
        deletedPost.setId(postId);
        deletedPost.setDeletedAt(new java.util.Date());

        // ServiceImpl.getById() 对 @TableLogic 实体会自动过滤已删除记录，
        // 返回 null，从而触发 ARTICLE_NOT_FOUND 异常
        when(postsMapper.selectActiveForUpdate(postId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postsAdminService.deletePostForAdmin(postId, operatorId));

        assertEquals(ErrorCode.ARTICLE_NOT_FOUND.getCode(), ex.getCode());

        verify(postTagsMapper, never()).deleteByPostId(anyLong());
        verify(postsMapper, never()).deleteById(anyLong(), any(), anyLong());
    }
    @Test
    void batchSoftDeleteAndRestoreKeepRecoverableRelations() {
        var ids = Arrays.asList(1L, 2L);
        Posts first = new Posts(); first.setId(1L); first.setDeletedAt(new java.util.Date());
        Posts second = new Posts(); second.setId(2L); second.setDeletedAt(new java.util.Date());
        when(postsMapper.selectForUpdateByIds(ids)).thenReturn(java.util.List.of(), java.util.List.of(first, second));
        when(postsMapper.update(isNull(), any())).thenReturn(2);
        when(postsMapper.restorePostsByIds(ids)).thenReturn(2);
        assertTrue(postsAdminService.removeByIds(ids));
        assertTrue(postsAdminService.batchRestorePosts(ids));
        verifyNoInteractions(postTagsMapper, postLikesMapper, postFavoritesMapper, postAttachmentsMapper);
    }

    @Test
    void permanentDeleteStillRemovesAllRelations() {
        Posts post = new Posts();
        post.setId(1L);
        when(postsMapper.selectByIdWithDeleted(1L)).thenReturn(post);
        when(postsMapper.permanentDeleteById(1L)).thenReturn(1);
        when(fileUtil.extractImageUrls(any())).thenReturn(Collections.emptyList());
        assertTrue(postsAdminService.permanentDeletePost(1L));
        verify(postTagsMapper).deleteByPostId(1L);
        verify(postLikesMapper).deleteByPostId(1L);
        verify(postFavoritesMapper).deleteByPostId(1L);
        verify(postAttachmentsMapper).deleteByPostId(1L);
    }

    @Test
    void articlePermanentDeleteReusesCommentTreeCleanupBeforeRemovingPost() {
        Posts post = new Posts();
        post.setId(1L);
        when(postsMapper.selectByIdWithDeleted(1L)).thenReturn(post);
        when(commentsMapper.selectRootCommentIdsByPostIds(Collections.singletonList(1L))).thenReturn(Arrays.asList(10L, 20L));
        when(postsMapper.permanentDeleteById(1L)).thenReturn(1);
        assertTrue(postsAdminService.permanentDeletePost(1L));
        var order = inOrder(commentsAdminService, postsMapper);
        order.verify(postsMapper).selectByIdWithDeleted(1L);
        order.verify(commentsAdminService).batchPermanentDeleteComments(Arrays.asList(10L, 20L));
        order.verify(postsMapper).permanentDeleteById(1L);
        verify(commentsMapper, never()).deleteChildrenByPostId(anyLong());
    }

}
