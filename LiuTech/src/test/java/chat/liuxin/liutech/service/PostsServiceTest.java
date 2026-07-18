package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.model.PostLikes;
import chat.liuxin.liutech.model.PostFavorites;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.req.PostCreateReq;
import chat.liuxin.liutech.req.PostQueryReq;
import chat.liuxin.liutech.req.PostUpdateReq;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostCreateResp;
import chat.liuxin.liutech.resp.PostDetailResp;
import chat.liuxin.liutech.resp.PostListResp;
import chat.liuxin.liutech.utils.FileUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostsService 单元测试
 * 覆盖文章增删改查、分页查询、权限校验等核心业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostsServiceTest {

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
    private ResourceDownloadService resourceDownloadService;

    @Mock
    private FileUtil fileUtil;

    @Mock
    private ImagesService imagesService;

    @InjectMocks
    private PostsService postsService;

    private static final Long POST_ID = 1L;
    private static final Long AUTHOR_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;

    @BeforeEach
    void setUp() {
        // ServiceImpl 需要 baseMapper 字段才能调用 getById/save/update 等方法
        ReflectionTestUtils.setField(postsService, "baseMapper", postsMapper);
        // LambdaUpdateWrapper 需要 TableInfo 缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Posts.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PostLikes.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PostFavorites.class);
    }

    // ========== 辅助方法 ==========

    private Posts createDefaultPost() {
        Posts post = new Posts();
        post.setId(POST_ID);
        post.setTitle("测试文章");
        post.setContent("测试内容");
        post.setSummary("测试摘要");
        post.setCategoryId(1L);
        post.setAuthorId(AUTHOR_ID);
        post.setStatus("published");
        post.setCoverImage("/uploads/cover.jpg");
        post.setThumbnail("/uploads/thumb.jpg");
        post.setViewCount(100);
        post.setLikeCount(10);
        post.setFavoriteCount(5);
        post.setCreatedAt(new Date());
        post.setUpdatedAt(new Date());
        post.setCreatedBy(AUTHOR_ID);
        post.setUpdatedBy(AUTHOR_ID);
        return post;
    }

    private PostDetailResp createDefaultPostDetail() {
        PostDetailResp detail = new PostDetailResp();
        detail.setId(POST_ID);
        detail.setTitle("测试文章");
        detail.setContent("测试内容");
        detail.setSummary("测试摘要");
        detail.setCategoryId(1L);
        detail.setAuthorId(AUTHOR_ID);
        detail.setStatus("published");
        detail.setViewCount(100);
        detail.setLikeCount(10);
        detail.setFavoriteCount(5);
        detail.setCreatedAt(new Date());
        PostDetailResp.AuthorInfo author = new PostDetailResp.AuthorInfo();
        author.setId(AUTHOR_ID);
        author.setUsername("testuser");
        detail.setAuthor(author);
        return detail;
    }

    private PostListResp createDefaultPostListResp() {
        PostListResp resp = new PostListResp();
        resp.setId(POST_ID);
        resp.setTitle("测试文章");
        resp.setSummary("测试摘要");
        resp.setCoverImage("/uploads/cover.jpg");
        resp.setThumbnail("/uploads/thumb.jpg");
        resp.setCategoryId(1L);
        resp.setAuthorId(AUTHOR_ID);
        resp.setStatus("published");
        resp.setViewCount(100);
        resp.setLikeCount(10);
        resp.setCreatedAt(new Date());
        PostListResp.AuthorInfo author = new PostListResp.AuthorInfo();
        author.setId(AUTHOR_ID);
        author.setUsername("testuser");
        resp.setAuthor(author);
        return resp;
    }

    private PostCreateReq createDefaultCreateReq() {
        PostCreateReq req = new PostCreateReq();
        req.setTitle("新文章");
        req.setContent("新内容");
        req.setSummary("新摘要");
        req.setCategoryId(1L);
        req.setTagIds(List.of(1L, 2L));
        req.setStatus("published");
        req.setDraftKey("draft-key-123");
        return req;
    }

    private PostUpdateReq createDefaultUpdateReq() {
        PostUpdateReq req = new PostUpdateReq();
        req.setId(POST_ID);
        req.setTitle("更新标题");
        req.setContent("更新内容");
        req.setSummary("更新摘要");
        req.setCategoryId(1L);
        req.setTagIds(List.of(1L));
        return req;
    }

    // ========== getPostList 测试 ==========

    @SuppressWarnings("unchecked")
    @Test
    void getPostList_shouldReturnPageWithResults() {
        PostQueryReq req = new PostQueryReq();
        req.setPage(1);
        req.setSize(10);

        PostListResp listResp = createDefaultPostListResp();
        List<PostListResp> records = new ArrayList<>(List.of(listResp));

        IPage<PostListResp> mockPage = mock(IPage.class);
        when(mockPage.getRecords()).thenReturn(records);
        when(mockPage.getTotal()).thenReturn(1L);
        when(mockPage.getCurrent()).thenReturn(1L);
        when(mockPage.getSize()).thenReturn(10L);

        when(postsMapper.selectPostListResl(any(Page.class), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull())).thenReturn(mockPage);

        // fillTags 调用 selectTagsByPostIds
        Map<String, Object> tagRow = new HashMap<>();
        tagRow.put("postId", POST_ID);
        tagRow.put("id", 1L);
        tagRow.put("name", "Java");
        when(postsMapper.selectTagsByPostIds(anyList())).thenReturn(List.of(tagRow));

        PageResp<PostListResp> result = postsService.getPostList(req);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(POST_ID, result.getRecords().get(0).getId());
        assertEquals(1L, result.getTotal());
        verify(postsMapper).selectPostListResl(any(Page.class), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull());
        verify(postsMapper).selectTagsByPostIds(anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getPostList_shouldReturnEmptyPage() {
        PostQueryReq req = new PostQueryReq();
        req.setPage(1);
        req.setSize(10);

        IPage<PostListResp> mockPage = mock(IPage.class);
        when(mockPage.getRecords()).thenReturn(new ArrayList<>());
        when(mockPage.getTotal()).thenReturn(0L);
        when(mockPage.getCurrent()).thenReturn(1L);
        when(mockPage.getSize()).thenReturn(10L);

        when(postsMapper.selectPostListResl(any(Page.class), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull())).thenReturn(mockPage);

        PageResp<PostListResp> result = postsService.getPostList(req);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0L, result.getTotal());
    }

    // ========== getPostDetail 测试 ==========

    @Test
    void getPostDetail_shouldReturnDetailAndIncrementViewCount() {
        PostDetailResp detail = createDefaultPostDetail();
        when(postsMapper.selectPostDetailResl(POST_ID, null)).thenReturn(detail);
        when(postAttachmentsMapper.selectPostAttachmentsPublic(POST_ID)).thenReturn(Collections.emptyList());
        when(postsMapper.update(isNull(), any())).thenReturn(1);

        PostDetailResp result = postsService.getPostDetail(POST_ID);

        assertNotNull(result);
        assertEquals(POST_ID, result.getId());
        assertEquals(101, result.getViewCount());
        verify(postsMapper).selectPostDetailResl(POST_ID, null);
        verify(postsMapper).update(isNull(), any());
    }

    @Test
    void getPostDetail_shouldReturnNullWhenNotFound() {
        when(postsMapper.selectPostDetailResl(POST_ID, null)).thenReturn(null);

        PostDetailResp result = postsService.getPostDetail(POST_ID);

        assertNull(result);
        verify(postsMapper).selectPostDetailResl(POST_ID, null);
        verify(postsMapper, never()).update(any(), any());
    }

    // ========== createPost 测试 ==========

    @Test
    void createPost_shouldCreatePostSuccessfully() {
        PostCreateReq req = createDefaultCreateReq();
        when(fileUtil.extractImageUrls(anyString())).thenReturn(Collections.emptyList());

        when(postsMapper.insert(any(Posts.class))).thenAnswer(invocation -> {
            Posts post = invocation.getArgument(0);
            post.setId(POST_ID);
            return 1;
        });
        when(postTagsMapper.batchInsert(anyList())).thenReturn(2);
        when(postAttachmentsMapper.bindDraftToPost("draft-key-123", POST_ID)).thenReturn(1);

        PostCreateResp result = postsService.createPost(req, AUTHOR_ID);

        assertNotNull(result);
        assertEquals(POST_ID, result.getId());
        assertEquals("新文章", result.getTitle());
        assertEquals("published", result.getStatus());
        verify(postsMapper).insert(any(Posts.class));
        verify(postTagsMapper).batchInsert(anyList());
        verify(postAttachmentsMapper).bindDraftToPost("draft-key-123", POST_ID);
    }

    @Test
    void createPost_shouldThrowWhenSaveFails() {
        PostCreateReq req = createDefaultCreateReq();
        when(postsMapper.insert(any(Posts.class))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> postsService.createPost(req, AUTHOR_ID));
    }

    // ========== updatePost 测试 ==========

    @Test
    void updatePost_shouldUpdateSuccessfully() {
        PostUpdateReq req = createDefaultUpdateReq();
        Posts existPost = createDefaultPost();
        when(postsMapper.selectById(POST_ID)).thenReturn(existPost);
        when(fileUtil.extractImageUrls(anyString())).thenReturn(Collections.emptyList());
        when(postsMapper.updateById(any(Posts.class))).thenReturn(1);
        when(postTagsMapper.deleteByPostId(POST_ID)).thenReturn(1);
        when(postTagsMapper.batchInsert(anyList())).thenReturn(1);

        boolean result = postsService.updatePost(req, AUTHOR_ID);

        assertTrue(result);
        verify(postsMapper).selectById(POST_ID);
        verify(postsMapper).updateById(any(Posts.class));
        verify(postTagsMapper).deleteByPostId(POST_ID);
        verify(postTagsMapper).batchInsert(anyList());
    }

    @Test
    void updatePost_shouldThrowWhenPostNotFound() {
        PostUpdateReq req = createDefaultUpdateReq();
        when(postsMapper.selectById(POST_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postsService.updatePost(req, AUTHOR_ID));
        assertEquals(1101, ex.getCode());
    }

    @Test
    void updatePost_shouldThrowWhenPermissionDenied() {
        PostUpdateReq req = createDefaultUpdateReq();
        Posts existPost = createDefaultPost();
        existPost.setAuthorId(AUTHOR_ID);
        when(postsMapper.selectById(POST_ID)).thenReturn(existPost);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postsService.updatePost(req, OTHER_USER_ID));
        assertEquals(1103, ex.getCode());
    }

    // ========== deletePost 测试 ==========

    @Test
    void deletePost_shouldDeleteSuccessfully() {
        Posts existPost = createDefaultPost();
        when(postsMapper.selectById(POST_ID)).thenReturn(existPost);
        when(postTagsMapper.deleteByPostId(POST_ID)).thenReturn(1);
        when(postLikesMapper.update(isNull(), any())).thenReturn(0);
        when(postFavoritesMapper.update(isNull(), any())).thenReturn(0);
        when(postsMapper.deleteById(eq(POST_ID), any(Date.class), eq(AUTHOR_ID))).thenReturn(1);

        boolean result = postsService.deletePost(POST_ID, AUTHOR_ID);

        assertTrue(result);
        verify(postTagsMapper).deleteByPostId(POST_ID);
        verify(postLikesMapper).update(isNull(), any());
        verify(postFavoritesMapper).update(isNull(), any());
        verify(postsMapper).deleteById(eq(POST_ID), any(Date.class), eq(AUTHOR_ID));
    }

    @Test
    void deletePost_shouldThrowWhenPostNotFound() {
        when(postsMapper.selectById(POST_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postsService.deletePost(POST_ID, AUTHOR_ID));
        assertEquals(1101, ex.getCode());
    }

    @Test
    void deletePost_shouldThrowWhenPermissionDenied() {
        Posts existPost = createDefaultPost();
        existPost.setAuthorId(AUTHOR_ID);
        when(postsMapper.selectById(POST_ID)).thenReturn(existPost);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postsService.deletePost(POST_ID, OTHER_USER_ID));
        assertEquals(1103, ex.getCode());
    }

    // ========== 系列相关 ==========

    @Test
    void createPost_shouldAutoAppendSeriesSortToTail() {
        PostCreateReq req = createDefaultCreateReq();
        req.setSeriesId(5L);
        req.setSeriesSort(99); // 前端传入应被忽略，由后端自动计算
        when(fileUtil.extractImageUrls(anyString())).thenReturn(Collections.emptyList());
        when(postsMapper.selectMaxSeriesSort(5L)).thenReturn(2);
        when(postsMapper.insert(any(Posts.class))).thenAnswer(invocation -> {
            Posts post = invocation.getArgument(0);
            post.setId(POST_ID);
            return 1;
        });
        when(postTagsMapper.batchInsert(anyList())).thenReturn(2);
        when(postAttachmentsMapper.bindDraftToPost("draft-key-123", POST_ID)).thenReturn(1);

        ArgumentCaptor<Posts> captor = ArgumentCaptor.forClass(Posts.class);
        postsService.createPost(req, AUTHOR_ID);

        verify(postsMapper).insert(captor.capture());
        assertEquals(5L, captor.getValue().getSeriesId());
        assertEquals(3, captor.getValue().getSeriesSort()); // max(2)+1
    }

    @Test
    void createPost_shouldSetSeriesSortZeroWhenNoSeries() {
        PostCreateReq req = createDefaultCreateReq();
        req.setSeriesId(null);
        when(fileUtil.extractImageUrls(anyString())).thenReturn(Collections.emptyList());
        when(postsMapper.insert(any(Posts.class))).thenAnswer(invocation -> {
            Posts post = invocation.getArgument(0);
            post.setId(POST_ID);
            return 1;
        });
        when(postTagsMapper.batchInsert(anyList())).thenReturn(2);
        when(postAttachmentsMapper.bindDraftToPost("draft-key-123", POST_ID)).thenReturn(1);

        ArgumentCaptor<Posts> captor = ArgumentCaptor.forClass(Posts.class);
        postsService.createPost(req, AUTHOR_ID);

        verify(postsMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getSeriesSort());
    }

    @Test
    void getPostDetail_shouldFillSeriesCatalogWhenPostInSeries() {
        PostDetailResp detail = createDefaultPostDetail();
        PostDetailResp.SeriesInfo series = new PostDetailResp.SeriesInfo();
        series.setId(7L);
        series.setName("Spring");
        detail.setSeries(series);
        when(postsMapper.selectPostDetailResl(POST_ID, null)).thenReturn(detail);
        when(postAttachmentsMapper.selectPostAttachmentsPublic(POST_ID)).thenReturn(Collections.emptyList());
        when(postsMapper.update(isNull(), any())).thenReturn(1);

        Posts catalogPost = new Posts();
        catalogPost.setId(POST_ID);
        catalogPost.setTitle("测试文章");
        catalogPost.setSeriesSort(0);
        when(postsMapper.selectSeriesPostCatalog(7L)).thenReturn(List.of(catalogPost));

        PostDetailResp result = postsService.getPostDetail(POST_ID);

        assertNotNull(result.getSeriesCatalog());
        assertEquals(1, result.getSeriesCatalog().size());
        assertEquals(POST_ID, result.getSeriesCatalog().get(0).getId());
        assertTrue(result.getSeriesCatalog().get(0).getCurrent());
        verify(postsMapper).selectSeriesPostCatalog(7L);
    }

    @Test
    void getPostDetail_shouldSkipSeriesCatalogWhenNoSeries() {
        PostDetailResp detail = createDefaultPostDetail();
        detail.setSeries(null);
        when(postsMapper.selectPostDetailResl(POST_ID, null)).thenReturn(detail);
        when(postAttachmentsMapper.selectPostAttachmentsPublic(POST_ID)).thenReturn(Collections.emptyList());
        when(postsMapper.update(isNull(), any())).thenReturn(1);

        PostDetailResp result = postsService.getPostDetail(POST_ID);

        assertNull(result.getSeriesCatalog());
        verify(postsMapper, never()).selectSeriesPostCatalog(any());
    }
}
