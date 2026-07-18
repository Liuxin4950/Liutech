package chat.liuxin.liutech.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.PostTagsMapper;
import chat.liuxin.liutech.mapper.PostLikesMapper;
import chat.liuxin.liutech.mapper.PostFavoritesMapper;
import chat.liuxin.liutech.mapper.PostAttachmentsMapper;
import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.model.PostTags;
import chat.liuxin.liutech.model.PostLikes;
import chat.liuxin.liutech.model.PostFavorites;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostDetailResp;
import chat.liuxin.liutech.resp.PostListResp;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.utils.FileUtil;

/**
 * 文章管理端服务类
 * 提供管理端专属的文章操作，包括无权限检查的状态修改、批量操作、物理删除等
 *
 * @author 刘鑫
 * @date 2025-01-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostsAdminService extends ServiceImpl<PostsMapper, Posts> {

    private final PostsMapper postsMapper;

    private final PostTagsMapper postTagsMapper;

    private final PostLikesMapper postLikesMapper;

    private final PostFavoritesMapper postFavoritesMapper;

    private final PostAttachmentsMapper postAttachmentsMapper;

    private final CommentsMapper commentsMapper;

    private final FileUtil fileUtil;

    private final ImagesService imagesService;

    private final PostsService postsService;

    /**
     * 管理端分页查询文章列表
     */
    @Transactional(readOnly = true)
    public PageResp<PostListResp> getPostListForAdmin(int page, int size, String title, Long categoryId, String status,
                                                      Long authorId, Long seriesId, Boolean includeDeleted) {
        log.debug("管理端查询文章列表 - 页码: {}, 每页: {}, 标题: {}, 分类: {}, 状态: {}, 作者: {}, 系列: {}, 包含已删除: {}",
                page, size, title, categoryId, status, authorId, seriesId, includeDeleted);

        try {
            Page<PostListResp> pageObj = new Page<>(page, size);
            String keyword = StringUtils.hasText(title) ? title.trim() : null;

            IPage<PostListResp> result = postsMapper.selectPostListForAdmin(pageObj, categoryId, keyword, status,
                    authorId, seriesId, includeDeleted);

            log.debug("管理端文章列表查询成功 - 总数: {}, 当前页数据: {}", result.getTotal(), result.getRecords().size());

            postsService.fillTags(result.getRecords());
            result.getRecords().forEach(postsService::normalizePostListUrls);
            return new PageResp<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());

        } catch (Exception e) {
            log.error("管理端文章列表查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询文章列表失败: " + e.getMessage());
        }
    }

    /**
     * 管理端查询文章详情（不增加访问量）
     */
    @Transactional(readOnly = true)
    public PostDetailResp getPostDetailForAdmin(Long id) {
        PostDetailResp postDetail = postsMapper.selectPostDetailResl(id, null);
        if (postDetail == null) {
            return null;
        }
        List<java.util.Map<String, Object>> list = postAttachmentsMapper.selectPostAttachmentsPublic(id);
        if (list != null && !list.isEmpty()) {
            List<PostDetailResp.AttachmentInfo> attachments = list.stream().map(map -> {
                PostDetailResp.AttachmentInfo a = new PostDetailResp.AttachmentInfo();
                Object v;
                v = map.get("attachmentId"); if (v != null) a.setAttachmentId(((Number) v).longValue());
                v = map.get("resourceId"); if (v != null) a.setResourceId(((Number) v).longValue());
                v = map.get("fileName"); if (v != null) a.setFileName(String.valueOf(v));
                v = map.get("fileUrl"); if (v != null) a.setFileUrl(String.valueOf(v));
                v = map.get("pointsNeeded"); if (v != null) a.setPointsNeeded(((Number) v).intValue());
                v = map.get("createdTime"); if (v instanceof java.util.Date) a.setCreatedTime((java.util.Date) v);
                v = map.get("externalLink"); if (v != null) a.setExternalLink(String.valueOf(v));
                v = map.get("resourceType"); if (v != null) a.setResourceType(String.valueOf(v));
                v = map.get("purchasedNote"); if (v != null) a.setPurchasedNote(String.valueOf(v));
                return a;
            }).collect(Collectors.toList());
            postDetail.setAttachments(attachments);
        }
        postsService.normalizePostDetailUrls(postDetail);
        return postDetail;
    }

    /**
     * 管理端更新文章状态（无权限检查）
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "postSeries", "allTags" }, allEntries = true)
    public boolean updatePostStatusForAdmin(Long id, String status, Long operatorId) {
        log.debug("管理端更新文章状态 - 文章ID: {}, 新状态: {}, 操作者: {}", id, status, operatorId);

        try {
            Posts existPost = this.getById(id);
            if (existPost == null || existPost.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
            }

            LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Posts::getId, id)
                    .set(Posts::getStatus, status)
                    .set(Posts::getUpdatedAt, new Date())
                    .set(Posts::getUpdatedBy, operatorId);

            boolean result = this.update(updateWrapper);
            log.debug("管理端文章状态更新{} - 文章ID: {}", result ? "成功" : "失败", id);
            return result;

        } catch (Exception e) {
            log.error("管理端更新文章状态失败 - 文章ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("更新文章状态失败: " + e.getMessage());
        }
    }

    /**
     * 管理端删除文章（软删除，无权限检查）
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "postSeries", "allTags" }, allEntries = true)
    public boolean deletePostForAdmin(Long id, Long operatorId) {
        log.debug("管理端删除文章 - 文章ID: {}, 操作者: {}", id, operatorId);

        try {
            Posts existPost = this.getById(id);
            if (existPost == null || existPost.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
            }

            postTagsMapper.deleteByPostId(id);

            LambdaUpdateWrapper<PostLikes> likeUpdateWrapper = new LambdaUpdateWrapper<>();
            likeUpdateWrapper.eq(PostLikes::getPostId, id)
                    .set(PostLikes::getDeletedAt, new Date());
            postLikesMapper.update(null, likeUpdateWrapper);

            LambdaUpdateWrapper<PostFavorites> favoriteUpdateWrapper = new LambdaUpdateWrapper<>();
            favoriteUpdateWrapper.eq(PostFavorites::getPostId, id)
                    .set(PostFavorites::getDeletedAt, new Date());
            postFavoritesMapper.update(null, favoriteUpdateWrapper);

            int result = postsMapper.deleteById(id, new Date(), operatorId);
            boolean success = result > 0;
            log.debug("管理端文章删除{} - 文章ID: {}", success ? "成功" : "失败", id);
            return success;

        } catch (Exception e) {
            log.error("管理端删除文章失败 - 文章ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("删除文章失败: " + e.getMessage());
        }
    }

    /**
     * 管理端批量更新文章状态
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "postSeries", "allTags" }, allEntries = true)
    public boolean batchUpdateStatus(List<Long> ids, String status) {
        log.debug("管理端批量更新文章状态 - 文章数量: {}, 新状态: {}", ids.size(), status);

        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }

            LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.in(Posts::getId, ids)
                    .set(Posts::getStatus, status)
                    .set(Posts::getUpdatedAt, new Date());

            boolean result = this.update(updateWrapper);
            log.debug("管理端批量更新文章状态{} - 影响文章数: {}", result ? "成功" : "失败", ids.size());
            return result;

        } catch (Exception e) {
            log.error("管理端批量更新文章状态失败 - 错误: {}", e.getMessage(), e);
            throw new RuntimeException("批量更新文章状态失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除文章（管理端）- 软删除
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "postSeries", "allTags" }, allEntries = true)
    public boolean removeByIds(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }

            LambdaQueryWrapper<PostTags> tagQueryWrapper = new LambdaQueryWrapper<>();
            tagQueryWrapper.in(PostTags::getPostId, ids);
            postTagsMapper.delete(tagQueryWrapper);

            LambdaUpdateWrapper<PostLikes> likesUpdateWrapper = new LambdaUpdateWrapper<>();
            likesUpdateWrapper.in(PostLikes::getPostId, ids)
                    .set(PostLikes::getDeletedAt, new Date());
            postLikesMapper.update(null, likesUpdateWrapper);

            LambdaUpdateWrapper<PostFavorites> favoritesUpdateWrapper = new LambdaUpdateWrapper<>();
            favoritesUpdateWrapper.in(PostFavorites::getPostId, ids)
                    .set(PostFavorites::getDeletedAt, new Date());
            postFavoritesMapper.update(null, favoritesUpdateWrapper);

            LambdaUpdateWrapper<Posts> postsUpdateWrapper = new LambdaUpdateWrapper<>();
            postsUpdateWrapper.in(Posts::getId, ids)
                    .set(Posts::getDeletedAt, new Date());

            int result = postsMapper.update(null, postsUpdateWrapper);
            log.debug("管理端批量删除文章{} - 影响文章数: {}", result > 0 ? "成功" : "失败", ids.size());
            return result > 0;
        } catch (Exception e) {
            log.error("批量删除文章失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "批量删除文章失败");
        }
    }

    /**
     * 恢复已删除的文章
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "postSeries", "allTags" }, allEntries = true)
    public boolean restorePost(Long id) {
        try {
            if (id == null) {
                return false;
            }
            int result = postsMapper.restorePostById(id);
            log.debug("恢复文章ID: {}, 结果: {}", id, result > 0 ? "成功" : "失败");
            return result > 0;
        } catch (Exception e) {
            log.error("恢复文章失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "恢复文章失败");
        }
    }

    /**
     * 批量恢复已删除的文章
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "postSeries", "allTags" }, allEntries = true)
    public boolean batchRestorePosts(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                log.warn("文章ID列表不能为空");
                return false;
            }
            int result = postsMapper.restorePostsByIds(ids);
            log.debug("批量恢复文章ID列表: {}, 成功数量: {}", ids, result);
            return result > 0;
        } catch (Exception e) {
            log.error("批量恢复文章失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "批量恢复文章失败");
        }
    }

    /**
     * 彻底删除文章（物理删除）
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "postSeries", "allTags" }, allEntries = true)
    public boolean permanentDeletePost(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("文章ID不能为空");
        }
        try {
            Posts post = postsMapper.selectByIdWithDeleted(id);
            if (post == null) {
                log.warn("文章不存在或已被删除，文章ID: {}", id);
                return false;
            }

            List<String> imageUrls = collectPostImageUrls(post.getCoverImage(), post.getThumbnail(), post.getContent());

            postFavoritesMapper.deleteByPostId(id);
            postLikesMapper.deleteByPostId(id);
            commentsMapper.deleteChildrenByPostId(id);
            commentsMapper.deleteRootsByPostId(id);
            postTagsMapper.deleteByPostId(id);
            postAttachmentsMapper.deleteByPostId(id);

            int result = postsMapper.permanentDeleteById(id);
            if (result <= 0) {
                throw new RuntimeException("文章删除失败，可能文章不存在");
            }

            for (String url : imageUrls) {
                imagesService.decrementImageUsageCountByUrl(url);
            }

            log.debug("彻底删除文章成功，文章ID: {}", id);
            return true;
        } catch (Exception e) {
            log.error("彻底删除文章失败，文章ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("彻底删除文章失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量彻底删除文章（物理删除）
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "postSeries", "allTags" }, allEntries = true)
    public boolean batchPermanentDeletePosts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("文章ID列表不能为空");
        }
        try {
            List<String> imageUrls = new ArrayList<>();
            for (Long postId : ids) {
                Posts post = postsMapper.selectByIdWithDeleted(postId);
                if (post == null) {
                    continue;
                }
                imageUrls.addAll(collectPostImageUrls(post.getCoverImage(), post.getThumbnail(), post.getContent()));
            }

            postFavoritesMapper.deleteByPostIds(ids);
            postLikesMapper.deleteByPostIds(ids);
            commentsMapper.deleteChildrenByPostIds(ids);
            commentsMapper.deleteRootsByPostIds(ids);
            postTagsMapper.deleteByPostIds(ids);
            postAttachmentsMapper.deleteByPostIds(ids);

            postsMapper.permanentDeleteByIds(ids);

            for (String url : imageUrls) {
                imagesService.decrementImageUsageCountByUrl(url);
            }

            log.debug("批量彻底删除文章成功，文章ID: {}", ids);
            return true;
        } catch (Exception e) {
            log.error("批量彻底删除文章失败，文章ID: {}, 错误: {}", ids, e.getMessage(), e);
            throw new RuntimeException("批量彻底删除文章失败: " + e.getMessage(), e);
        }
    }

    private List<String> collectPostImageUrls(String coverImage, String thumbnail, String content) {
        List<String> urls = new ArrayList<>();
        if (StringUtils.hasText(coverImage)) {
            urls.add(coverImage);
        }
        if (StringUtils.hasText(thumbnail)) {
            urls.add(thumbnail);
        }
        urls.addAll(fileUtil.extractImageUrls(content));
        return urls;
    }
}
