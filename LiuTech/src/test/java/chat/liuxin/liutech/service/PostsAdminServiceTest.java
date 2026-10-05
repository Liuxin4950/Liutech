package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.*;
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

        when(postsMapper.selectById(postId)).thenReturn(post);
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

        when(postsMapper.selectById(postId)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> postsAdminService.deletePostForAdmin(postId, operatorId));

        // catch 块用 new RuntimeException(msg) 包装，未链式传递 cause
        assertTrue(ex.getMessage().contains("删除文章失败"));

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
        when(postsMapper.selectById(postId)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> postsAdminService.deletePostForAdmin(postId, operatorId));

        assertTrue(ex.getMessage().contains("删除文章失败"));

        verify(postTagsMapper, never()).deleteByPostId(anyLong());
        verify(postsMapper, never()).deleteById(anyLong(), any(), anyLong());
    }
    @Test
    void batchSoftDeleteAndRestoreKeepRecoverableRelations() {
        var ids = Arrays.asList(1L, 2L);
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
