package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.PostTagsMapper;
import chat.liuxin.liutech.mapper.PostLikesMapper;
import chat.liuxin.liutech.mapper.PostFavoritesMapper;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.model.PostTags;
import chat.liuxin.liutech.model.PostLikes;
import chat.liuxin.liutech.model.PostFavorites;
import chat.liuxin.liutech.req.PostCreateReq;
import chat.liuxin.liutech.req.PostQueryReq;
import chat.liuxin.liutech.req.PostUpdateReq;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.resl.PostCreateResl;
import chat.liuxin.liutech.resl.PostDetailResl;
import chat.liuxin.liutech.resl.PostListResl;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.BusinessException;


/**
 * 文章服务类
 */
@Slf4j
@Service
public class PostsService extends ServiceImpl<PostsMapper, Posts> {

    @Autowired
    private PostsMapper postsMapper;
    
    @Autowired
    private PostTagsMapper postTagsMapper;
    
    @Autowired
    private PostLikesMapper postLikesMapper;
    
    @Autowired
    private PostFavoritesMapper postFavoritesMapper;

    /**
     * 分页查询文章列表
     * @param req 查询请求
     * @return 分页结果
     */
    public PageResl<PostListResl> getPostList(PostQueryReq req) {
        return getPostList(req, null);
    }
    
    /**
     * 分页查询文章列表（支持用户状态）
     * @param req 查询请求
     * @param userId 当前用户ID（可为null）
     * @return 分页结果
     */
    public PageResl<PostListResl> getPostList(PostQueryReq req, Long userId) {
        // 创建分页对象
        Page<PostListResl> page = new Page<>(req.getPage(), req.getSize());
        
        // 处理搜索关键词
        String keyword = StringUtils.hasText(req.getKeyword()) ? req.getKeyword().trim() : null;
        
        // 执行分页查询，直接返回PostListResl
        IPage<PostListResl> result = postsMapper.selectPostListResl(page, req.getCategoryId(), req.getTagId(), keyword, req.getStatus(), req.getAuthorId(), userId);
        
        // 使用MyBatis-Plus自动统计的总数
        return new PageResl<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 根据ID查询文章详情
     * @param id 文章ID
     * @return 文章详情
     */
    @Transactional(rollbackFor = Exception.class)
    public PostDetailResl getPostDetail(Long id) {
        return getPostDetail(id, null);
    }
    
    /**
     * 根据ID查询文章详情（包含用户状态）
     * @param id 文章ID
     * @param userId 当前用户ID（可为null）
     * @return 文章详情
     */
    @Transactional(rollbackFor = Exception.class)
    public PostDetailResl getPostDetail(Long id, Long userId) {
        PostDetailResl postDetail = postsMapper.selectPostDetailResl(id, userId);
        if (postDetail == null) {
            return null;
        }
        
        // 访问数自增
        LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Posts::getId, id)
                     .setSql("view_count = IFNULL(view_count, 0) + 1");
        this.update(updateWrapper);
        
        // 更新返回对象中的访问数
        if (postDetail.getViewCount() == null) {
            postDetail.setViewCount(1);
        } else {
            postDetail.setViewCount(postDetail.getViewCount() + 1);
        }
        
        return postDetail;
    }
    
    /**
     * 点赞文章（已废弃，请使用toggleLike方法）
     * @param id 文章ID
     * @return 是否成功
     * @deprecated 使用toggleLike(Long postId, Long userId)替代
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean likePost(Long id) {
        // 检查文章是否存在
        Posts post = this.getById(id);
        if (post == null || post.getDeletedAt() != null) {
            return false;
        }
        
        // 点赞数自增
        LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Posts::getId, id)
                     .setSql("like_count = IFNULL(like_count, 0) + 1");
        return this.update(updateWrapper);
    }
    
    /**
     * 切换文章点赞状态
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 点赞后的状态（true=已点赞，false=已取消点赞）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long postId, Long userId) {
        // 检查文章是否存在
        Posts post = this.getById(postId);
        if (post == null || post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "文章不存在");
        }
        
        // 查询当前点赞状态
        Integer currentStatus = postLikesMapper.getLikeStatus(userId, postId);
        boolean isLiked = currentStatus != null && currentStatus == 1;
        
        // 切换点赞状态
        boolean newStatus = !isLiked;
        postLikesMapper.insertOrUpdateLike(userId, postId, newStatus ? 1 : 0);
        
        // 更新文章点赞数
        Integer likeCount = postLikesMapper.countLikesByPostId(postId);
        LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Posts::getId, postId)
                     .set(Posts::getLikeCount, likeCount);
        this.update(updateWrapper);
        
        return newStatus;
    }
    
    /**
     * 切换文章收藏状态
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 收藏后的状态（true=已收藏，false=已取消收藏）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long postId, Long userId) {
        // 检查文章是否存在
        Posts post = this.getById(postId);
        if (post == null || post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "文章不存在");
        }
        
        // 查询当前收藏状态
        Integer currentStatus = postFavoritesMapper.getFavoriteStatus(userId, postId);
        boolean isFavorited = currentStatus != null && currentStatus == 1;
        
        // 切换收藏状态
        boolean newStatus = !isFavorited;
        postFavoritesMapper.insertOrUpdateFavorite(userId, postId, newStatus ? 1 : 0);
        
        // 更新文章收藏数
        Integer favoriteCount = postFavoritesMapper.countFavoritesByPostId(postId);
        LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Posts::getId, postId)
                     .set(Posts::getFavoriteCount, favoriteCount);
        this.update(updateWrapper);
        
        return newStatus;
    }

    /**
     * 查询热门文章
     * @param limit 限制数量
     * @return 热门文章列表
     */
    @Cacheable(value = "hotPosts", key = "#limit", unless = "#result == null || #result.isEmpty()")
    public List<PostListResl> getHotPosts(Integer limit) {
        return getHotPosts(limit, null);
    }
    
    /**
     * 查询热门文章（支持用户状态）
     * @param limit 限制数量
     * @param userId 当前用户ID（可为null）
     * @return 热门文章列表
     */
    public List<PostListResl> getHotPosts(Integer limit, Long userId) {
        return postsMapper.selectHotPostListResl(limit, userId);
    }

    /**
     * 查询最新文章
     * @param limit 限制数量
     * @return 最新文章列表
     */
    @Cacheable(value = "latestPosts", key = "#limit", unless = "#result == null || #result.isEmpty()")
    public List<PostListResl> getLatestPosts(Integer limit) {
        return getLatestPosts(limit, null);
    }
    
    /**
     * 查询最新文章（支持用户状态）
     * @param limit 限制数量
     * @param userId 当前用户ID（可为null）
     * @return 最新文章列表
     */
    public List<PostListResl> getLatestPosts(Integer limit, Long userId) {
        return postsMapper.selectLatestPostListResl(limit, userId);
    }



    /**
     * 创建文章
     * @param req 创建请求
     * @param authorId 作者ID
     * @return 文章创建响应
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"hotPosts", "latestPosts"}, allEntries = true)
    public PostCreateResl createPost(PostCreateReq req, Long authorId) {
        // 创建文章对象
        Posts post = new Posts();
        BeanUtils.copyProperties(req, post);
        post.setAuthorId(authorId);
        post.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "draft");
        post.setCreatedAt(new Date());
        post.setUpdatedAt(new Date());
        post.setCreatedBy(authorId);
        post.setUpdatedBy(authorId);
        
        // 保存文章
        boolean saved = this.save(post);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文章创建失败");
        }
        
        // 处理标签关联
        if (req.getTagIds() != null && !req.getTagIds().isEmpty()) {
            savePostTags(post.getId(), req.getTagIds());
        }
        
        // 构建响应对象
        PostCreateResl response = new PostCreateResl();
        response.setId(post.getId());
        response.setTitle(post.getTitle());
        response.setStatus(post.getStatus());
        response.setCreatedAt(post.getCreatedAt());
        
        return response;
    }

    /**
     * 更新文章
     * @param req 更新请求
     * @param authorId 作者ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"hotPosts", "latestPosts"}, allEntries = true)
    public boolean updatePost(PostUpdateReq req, Long authorId) {
        // 检查文章是否存在
        Posts existPost = this.getById(req.getId());
        if (existPost == null || existPost.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        
        // 检查权限（只有作者可以编辑）
        if (!existPost.getAuthorId().equals(authorId)) {
            throw new BusinessException(ErrorCode.ARTICLE_PERMISSION_DENIED);
        }
        
        // 更新文章信息
        Posts post = new Posts();
        BeanUtils.copyProperties(req, post);
        post.setUpdatedAt(new Date());
        post.setUpdatedBy(authorId);
        
        boolean updated = this.updateById(post);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文章更新失败");
        }
        
        // 更新标签关联
        updatePostTags(req.getId(), req.getTagIds());
        
        return true;
    }

    /**
     * 删除文章（软删除）
     * @param id 文章ID
     * @param authorId 作者ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"hotPosts", "latestPosts"}, allEntries = true)
    public boolean deletePost(Long id, Long authorId) {
        // 检查文章是否存在
        Posts existPost = this.getById(id);
        if (existPost == null || existPost.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        
        // 检查权限（只有作者可以删除）
        if (!existPost.getAuthorId().equals(authorId)) {
            throw new BusinessException(ErrorCode.ARTICLE_PERMISSION_DENIED);
        }
        
        // 软删除文章
        int result = postsMapper.deleteById(id, new Date(), authorId);
        return result > 0;
    }

    /**
     * 发布文章
     * @param id 文章ID
     * @param authorId 作者ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"hotPosts", "latestPosts"}, allEntries = true)
    public boolean publishPost(Long id, Long authorId) {
        return updatePostStatus(id, "published", authorId);
    }

    /**
     * 取消发布文章
     * @param id 文章ID
     * @param authorId 作者ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean unpublishPost(Long id, Long authorId) {
        return updatePostStatus(id, "draft", authorId);
    }

    /**
     * 更新文章状态
     * @param id 文章ID
     * @param status 状态
     * @param authorId 作者ID
     * @return 是否成功
     */
    private boolean updatePostStatus(Long id, String status, Long authorId) {
        // 检查文章是否存在
        Posts existPost = this.getById(id);
        if (existPost == null || existPost.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        
        // 检查权限
        if (!existPost.getAuthorId().equals(authorId)) {
            throw new BusinessException(ErrorCode.ARTICLE_PERMISSION_DENIED);
        }
        
        // 使用 LambdaUpdateWrapper 只更新指定字段
        LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Posts::getId, id)
                    .set(Posts::getStatus, status)
                    .set(Posts::getUpdatedAt, new Date())
                    .set(Posts::getUpdatedBy, authorId);
        
        return this.update(updateWrapper);
    }

    /**
     * 保存文章标签关联
     * @param postId 文章ID
     * @param tagIds 标签ID列表
     */
    private void savePostTags(Long postId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        
        List<PostTags> postTags = tagIds.stream()
                .map(tagId -> {
                    PostTags postTag = new PostTags();
                    postTag.setPostId(postId);
                    postTag.setTagId(tagId);
                    return postTag;
                })
                .collect(Collectors.toList());
        
        postTagsMapper.batchInsert(postTags);
    }

    /**
     * 更新文章标签关联
     * @param postId 文章ID
     * @param tagIds 标签ID列表
     */
    private void updatePostTags(Long postId, List<Long> tagIds) {
        // 删除原有关联
        postTagsMapper.deleteByPostId(postId);
        
        // 添加新关联
        if (tagIds != null && !tagIds.isEmpty()) {
            savePostTags(postId, tagIds);
        }
    }
    
    /**
     * 统计用户文章数量（已发布）
     * @param userId 用户ID
     * @return 文章数量
     */
    public Integer countPublishedPostsByUserId(Long userId) {
        return postsMapper.countPostsByUserIdAndStatus(userId, "published");
    }
    
    /**
     * 统计用户草稿数量
     * @param userId 用户ID
     * @return 草稿数量
     */
    public Integer countDraftsByUserId(Long userId) {
        return postsMapper.countPostsByUserIdAndStatus(userId, "draft");
    }
    
    /**
     * 获取用户最后发文时间
     * @param userId 用户ID
     * @return 最后发文时间
     */
    public Date getLastPostTimeByUserId(Long userId) {
        return postsMapper.getLastPostTimeByUserId(userId);
    }
    
    /**
     * 统计用户文章数量（按状态）
     * @param userId 用户ID
     * @param status 文章状态
     * @return 文章数量
     */
    public Integer countPostsByUserId(Long userId, String status) {
        return postsMapper.countPostsByUserIdAndStatus(userId, status);
    }
    
    /**
     * 统计全站已发布文章数量
     * @return 已发布文章数量
     */
    public Integer countAllPublishedPosts() {
        return postsMapper.countAllPublishedPosts();
    }
    
    /**
     * 统计全站文章总浏览量
     * @return 总浏览量
     */
    public Long countAllViews() {
        return postsMapper.countAllViews();
    }
    
    /**
     * 统计用户所有文章的浏览量总和
     * @param userId 用户ID
     * @return 用户文章总浏览量
     */
    public Long countViewsByUserId(Long userId) {
        return postsMapper.countViewsByUserId(userId);
    }
    
    /**
     * 管理端分页查询文章列表
     * 支持按标题、分类ID、状态、作者ID过滤
     * 
     * @author 刘鑫
     * @date 2025-01-17
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @param title 标题关键词（可选）
     * @param categoryId 分类ID（可选）
     * @param status 状态（可选）
     * @param authorId 作者ID（可选）
     * @return 分页文章列表
     */
    public PageResl<PostListResl> getPostListForAdmin(int page, int size, String title, Long categoryId, String status, Long authorId) {
        log.info("管理端查询文章列表 - 页码: {}, 每页: {}, 标题: {}, 分类: {}, 状态: {}, 作者: {}", 
                page, size, title, categoryId, status, authorId);
        
        try {
            // 创建分页对象
            Page<PostListResl> pageObj = new Page<>(page, size);
            
            // 处理搜索关键词
            String keyword = StringUtils.hasText(title) ? title.trim() : null;
            
            // 执行分页查询，复用现有的selectPostListResl方法
            IPage<PostListResl> result = postsMapper.selectPostListResl(pageObj, categoryId, null, keyword, status, authorId, null);
            
            log.info("管理端文章列表查询成功 - 总数: {}, 当前页数据: {}", result.getTotal(), result.getRecords().size());
            
            // 使用MyBatis-Plus自动统计的总数
            return new PageResl<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
            
        } catch (Exception e) {
            log.error("管理端文章列表查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询文章列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 管理端更新文章状态
     * 管理员可以更新任何文章的状态
     * 
     * @author 刘鑫
     * @date 2025-01-17
     * @param id 文章ID
     * @param status 新状态
     * @param operatorId 操作者ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"hotPosts", "latestPosts"}, allEntries = true)
    public boolean updatePostStatusForAdmin(Long id, String status, Long operatorId) {
        log.info("管理端更新文章状态 - 文章ID: {}, 新状态: {}, 操作者: {}", id, status, operatorId);
        
        try {
            // 检查文章是否存在
            Posts existPost = this.getById(id);
            if (existPost == null || existPost.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
            }
            
            // 管理员可以更新任何文章的状态，无需权限检查
            LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Posts::getId, id)
                        .set(Posts::getStatus, status)
                        .set(Posts::getUpdatedAt, new Date())
                        .set(Posts::getUpdatedBy, operatorId);
            
            boolean result = this.update(updateWrapper);
            log.info("管理端文章状态更新{} - 文章ID: {}", result ? "成功" : "失败", id);
            return result;
            
        } catch (Exception e) {
            log.error("管理端更新文章状态失败 - 文章ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("更新文章状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 管理端删除文章（软删除）
     * 管理员可以删除任何文章
     * 
     * @author 刘鑫
     * @date 2025-01-17
     * @param id 文章ID
     * @param operatorId 操作者ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"hotPosts", "latestPosts"}, allEntries = true)
    public boolean deletePostForAdmin(Long id, Long operatorId) {
        log.info("管理端删除文章 - 文章ID: {}, 操作者: {}", id, operatorId);
        
        try {
            // 检查文章是否存在
            Posts existPost = this.getById(id);
            if (existPost == null || existPost.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
            }
            
            // 管理员可以删除任何文章，无需权限检查
            int result = postsMapper.deleteById(id, new Date(), operatorId);
            boolean success = result > 0;
            log.info("管理端文章删除{} - 文章ID: {}", success ? "成功" : "失败", id);
            return success;
            
        } catch (Exception e) {
            log.error("管理端删除文章失败 - 文章ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("删除文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 管理端批量更新文章状态
     * 管理员可以批量更新文章状态
     * 
     * @author 刘鑫
     * @date 2025-01-17
     * @param ids 文章ID列表
     * @param status 新状态
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"hotPosts", "latestPosts"}, allEntries = true)
    public boolean batchUpdateStatus(List<Long> ids, String status) {
        log.info("管理端批量更新文章状态 - 文章数量: {}, 新状态: {}", ids.size(), status);
        
        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }
            
            // 批量更新文章状态
            LambdaUpdateWrapper<Posts> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.in(Posts::getId, ids)
                        .set(Posts::getStatus, status)
                        .set(Posts::getUpdatedAt, new Date());
            
            boolean result = this.update(updateWrapper);
            log.info("管理端批量更新文章状态{} - 影响文章数: {}", result ? "成功" : "失败", ids.size());
            return result;
            
        } catch (Exception e) {
            log.error("管理端批量更新文章状态失败 - 错误: {}", e.getMessage(), e);
            throw new RuntimeException("批量更新文章状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量删除文章（管理端）- 软删除
     * 同时删除相关的点赞、收藏、评论和标签关联
     * 
     * @author 刘鑫
     * @date 2025-01-17
     * @param ids 文章ID列表
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"hotPosts", "latestPosts"}, allEntries = true)
    public boolean removeByIds(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }
            
            // 删除文章标签关联
            LambdaQueryWrapper<PostTags> tagQueryWrapper = new LambdaQueryWrapper<>();
            tagQueryWrapper.in(PostTags::getPostId, ids);
            postTagsMapper.delete(tagQueryWrapper);
            
            // 软删除点赞记录
            LambdaUpdateWrapper<PostLikes> likesUpdateWrapper = new LambdaUpdateWrapper<>();
            likesUpdateWrapper.in(PostLikes::getPostId, ids)
                    .set(PostLikes::getIsLike, 0)
                    .set(PostLikes::getUpdatedAt, new Date());
            postLikesMapper.update(null, likesUpdateWrapper);
            
            // 软删除收藏记录
            LambdaUpdateWrapper<PostFavorites> favoritesUpdateWrapper = new LambdaUpdateWrapper<>();
            favoritesUpdateWrapper.in(PostFavorites::getPostId, ids)
                    .set(PostFavorites::getIsFavorite, 0)
                    .set(PostFavorites::getUpdatedAt, new Date());
            postFavoritesMapper.update(null, favoritesUpdateWrapper);
            
            // 软删除文章
            LambdaUpdateWrapper<Posts> postsUpdateWrapper = new LambdaUpdateWrapper<>();
            postsUpdateWrapper.in(Posts::getId, ids)
                    .set(Posts::getDeletedAt, new Date());
            
            int result = postsMapper.update(null, postsUpdateWrapper);
            log.info("管理端批量删除文章{} - 影响文章数: {}", result > 0 ? "成功" : "失败", ids.size());
            return result > 0;
        } catch (Exception e) {
            log.error("批量删除文章失败: {}", e.getMessage(), e);
            return false;
        }
    }
}