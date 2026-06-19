package chat.liuxin.liutech.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.PostFavoritesMapper;
import chat.liuxin.liutech.mapper.PostLikesMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.req.PostQueryReq;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostListResp;
import lombok.RequiredArgsConstructor;

/**
 * 文章交互服务（点赞、收藏）
 * 从 PostsService 中拆分，职责单一
 *
 * @author 刘鑫
 */
@Service
@RequiredArgsConstructor
public class PostInteractionService {

    private final PostsMapper postsMapper;
    private final PostLikesMapper postLikesMapper;
    private final PostFavoritesMapper postFavoritesMapper;
    private final PostsService postsService;

    /**
     * 切换文章点赞状态
     * 如果用户未点赞则点赞，如果已点赞则取消点赞，同时更新文章点赞数
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 点赞后的状态（true=已点赞，false=已取消点赞）
     * @throws BusinessException 当文章不存在时抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long postId, Long userId) {
        Posts post = postsService.getById(postId);
        if (post == null || post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "文章不存在");
        }

        Integer currentStatus = postLikesMapper.getLikeStatus(userId, postId);
        boolean isLiked = currentStatus != null && currentStatus == 1;

        boolean newStatus = !isLiked;
        postLikesMapper.insertOrUpdateLike(userId, postId, newStatus ? 1 : 0);

        Integer likeCount = postLikesMapper.countLikesByPostId(postId);
        LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Posts::getId, postId)
                .set(Posts::getLikeCount, likeCount);
        postsService.update(updateWrapper);

        return newStatus;
    }

    /**
     * 切换文章收藏状态
     * 如果用户未收藏则收藏，如果已收藏则取消收藏，同时更新文章收藏数
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 收藏后的状态（true=已收藏，false=已取消收藏）
     * @throws BusinessException 当文章不存在时抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long postId, Long userId) {
        Posts post = postsService.getById(postId);
        if (post == null || post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "文章不存在");
        }

        Integer currentStatus = postFavoritesMapper.getFavoriteStatus(userId, postId);
        boolean isFavorited = currentStatus != null && currentStatus == 1;

        boolean newStatus = !isFavorited;
        postFavoritesMapper.insertOrUpdateFavorite(userId, postId, newStatus ? 1 : 0);

        Integer favoriteCount = postFavoritesMapper.countFavoritesByPostId(postId);
        LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Posts::getId, postId)
                .set(Posts::getFavoriteCount, favoriteCount);
        postsService.update(updateWrapper);

        return newStatus;
    }

    /**
     * 获取用户收藏的文章列表
     *
     * @param req    查询请求参数
     * @param userId 用户ID
     * @return 分页的文章列表
     */
    @Transactional(readOnly = true)
    public PageResp<PostListResp> getFavoritePosts(PostQueryReq req, Long userId) {
        Page<PostListResp> page = new Page<>(req.getPage(), req.getSize());
        String keyword = StringUtils.hasText(req.getKeyword()) ? req.getKeyword().trim() : null;

        IPage<PostListResp> result = postsMapper.selectFavoritePostList(page, userId, keyword);

        postsService.fillTags(result.getRecords());
        result.getRecords().forEach(postsService::normalizePostListUrls);
        return new PageResp<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
