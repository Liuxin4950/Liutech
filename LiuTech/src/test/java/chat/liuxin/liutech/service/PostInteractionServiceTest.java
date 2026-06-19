package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.PostFavoritesMapper;
import chat.liuxin.liutech.mapper.PostLikesMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.model.Posts;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostInteractionService 单元测试
 * 覆盖 toggleLike、toggleFavorite 的核心逻辑和异常路径
 */
@ExtendWith(MockitoExtension.class)
class PostInteractionServiceTest {

    @Mock
    private PostsMapper postsMapper;

    @Mock
    private PostLikesMapper postLikesMapper;

    @Mock
    private PostFavoritesMapper postFavoritesMapper;

    @Mock
    private PostsService postsService;

    @InjectMocks
    private PostInteractionService postInteractionService;

    @BeforeEach
    void setUp() {
        // PostsService 需要 baseMapper 才能调用 update
        ReflectionTestUtils.setField(postsService, "baseMapper", postsMapper);
        // LambdaUpdateWrapper 需要 TableInfo 缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Posts.class);
    }

    // ========== toggleLike 测试 ==========

    @Test
    void toggleLike_shouldReturnTrueWhenNotLiked() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);

        when(postsService.getById(postId)).thenReturn(post);
        when(postLikesMapper.getLikeStatus(userId, postId)).thenReturn(0);
        when(postLikesMapper.countLikesByPostId(postId)).thenReturn(5);
        when(postsService.update(any())).thenReturn(true);

        boolean result = postInteractionService.toggleLike(postId, userId);

        assertTrue(result);
        verify(postLikesMapper).insertOrUpdateLike(userId, postId, 1);
        verify(postsService).update(any());
    }

    @Test
    void toggleLike_shouldReturnFalseWhenAlreadyLiked() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);

        when(postsService.getById(postId)).thenReturn(post);
        when(postLikesMapper.getLikeStatus(userId, postId)).thenReturn(1);
        when(postLikesMapper.countLikesByPostId(postId)).thenReturn(4);
        when(postsService.update(any())).thenReturn(true);

        boolean result = postInteractionService.toggleLike(postId, userId);

        assertFalse(result);
        verify(postLikesMapper).insertOrUpdateLike(userId, postId, 0);
    }

    @Test
    void toggleLike_shouldReturnTrueWhenNoPreviousRecord() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);

        when(postsService.getById(postId)).thenReturn(post);
        when(postLikesMapper.getLikeStatus(userId, postId)).thenReturn(null);
        when(postLikesMapper.countLikesByPostId(postId)).thenReturn(1);
        when(postsService.update(any())).thenReturn(true);

        boolean result = postInteractionService.toggleLike(postId, userId);

        assertTrue(result);
        verify(postLikesMapper).insertOrUpdateLike(userId, postId, 1);
    }

    @Test
    void toggleLike_shouldThrowWhenPostNotFound() {
        Long postId = 999L;
        Long userId = 10L;

        when(postsService.getById(postId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postInteractionService.toggleLike(postId, userId));
        assertEquals(1101, ex.getCode());
    }

    @Test
    void toggleLike_shouldThrowWhenPostIsDeleted() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);
        post.setDeletedAt(new Date());

        when(postsService.getById(postId)).thenReturn(post);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postInteractionService.toggleLike(postId, userId));
        assertEquals(1101, ex.getCode());
    }

    @Test
    void toggleLike_shouldUpdatePostLikeCount() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);

        when(postsService.getById(postId)).thenReturn(post);
        when(postLikesMapper.getLikeStatus(userId, postId)).thenReturn(0);
        when(postLikesMapper.countLikesByPostId(postId)).thenReturn(10);
        when(postsService.update(any())).thenReturn(true);

        postInteractionService.toggleLike(postId, userId);

        verify(postLikesMapper).countLikesByPostId(postId);
        verify(postsService).update(any());
    }

    // ========== toggleFavorite 测试 ==========

    @Test
    void toggleFavorite_shouldReturnTrueWhenNotFavorited() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);

        when(postsService.getById(postId)).thenReturn(post);
        when(postFavoritesMapper.getFavoriteStatus(userId, postId)).thenReturn(0);
        when(postFavoritesMapper.countFavoritesByPostId(postId)).thenReturn(3);
        when(postsService.update(any())).thenReturn(true);

        boolean result = postInteractionService.toggleFavorite(postId, userId);

        assertTrue(result);
        verify(postFavoritesMapper).insertOrUpdateFavorite(userId, postId, 1);
        verify(postsService).update(any());
    }

    @Test
    void toggleFavorite_shouldReturnFalseWhenAlreadyFavorited() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);

        when(postsService.getById(postId)).thenReturn(post);
        when(postFavoritesMapper.getFavoriteStatus(userId, postId)).thenReturn(1);
        when(postFavoritesMapper.countFavoritesByPostId(postId)).thenReturn(2);
        when(postsService.update(any())).thenReturn(true);

        boolean result = postInteractionService.toggleFavorite(postId, userId);

        assertFalse(result);
        verify(postFavoritesMapper).insertOrUpdateFavorite(userId, postId, 0);
    }

    @Test
    void toggleFavorite_shouldReturnTrueWhenNoPreviousRecord() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);

        when(postsService.getById(postId)).thenReturn(post);
        when(postFavoritesMapper.getFavoriteStatus(userId, postId)).thenReturn(null);
        when(postFavoritesMapper.countFavoritesByPostId(postId)).thenReturn(1);
        when(postsService.update(any())).thenReturn(true);

        boolean result = postInteractionService.toggleFavorite(postId, userId);

        assertTrue(result);
        verify(postFavoritesMapper).insertOrUpdateFavorite(userId, postId, 1);
    }

    @Test
    void toggleFavorite_shouldThrowWhenPostNotFound() {
        Long postId = 999L;
        Long userId = 10L;

        when(postsService.getById(postId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postInteractionService.toggleFavorite(postId, userId));
        assertEquals(1101, ex.getCode());
    }

    @Test
    void toggleFavorite_shouldThrowWhenPostIsDeleted() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);
        post.setDeletedAt(new Date());

        when(postsService.getById(postId)).thenReturn(post);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postInteractionService.toggleFavorite(postId, userId));
        assertEquals(1101, ex.getCode());
    }

    @Test
    void toggleFavorite_shouldUpdatePostFavoriteCount() {
        Long postId = 1L;
        Long userId = 10L;
        Posts post = createPost(postId);

        when(postsService.getById(postId)).thenReturn(post);
        when(postFavoritesMapper.getFavoriteStatus(userId, postId)).thenReturn(0);
        when(postFavoritesMapper.countFavoritesByPostId(postId)).thenReturn(7);
        when(postsService.update(any())).thenReturn(true);

        postInteractionService.toggleFavorite(postId, userId);

        verify(postFavoritesMapper).countFavoritesByPostId(postId);
        verify(postsService).update(any());
    }

    // ========== 辅助方法 ==========

    private Posts createPost(Long id) {
        Posts post = new Posts();
        post.setId(id);
        post.setTitle("测试文章");
        post.setLikeCount(0);
        post.setFavoriteCount(0);
        return post;
    }
}
