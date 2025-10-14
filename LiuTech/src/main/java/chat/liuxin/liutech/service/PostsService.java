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
import chat.liuxin.liutech.mapper.PostAttachmentsMapper;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.model.PostTags;
import chat.liuxin.liutech.model.PostLikes;
import chat.liuxin.liutech.model.PostFavorites;
import chat.liuxin.liutech.req.PostCreateReq;
import chat.liuxin.liutech.req.PostQueryReq;
import chat.liuxin.liutech.req.PostUpdateReq;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostCreateResp;
import chat.liuxin.liutech.resp.PostDetailResp;
import chat.liuxin.liutech.resp.PostListResp;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.CommentsMapper;

/**
 * 文章服务类
 * 提供文章的增删改查、点赞收藏、统计等核心业务功能
 *
 * @author 刘鑫
 * @date 2025-08-30
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

    @Autowired
    private PostAttachmentsMapper postAttachmentsMapper;

    @Autowired
    private ResourceDownloadService resourceDownloadService;

    @Autowired
    private CommentsMapper commentsMapper;

    /**
     * 分页查询文章列表（公开接口）
     * 支持按分类、标签、关键词、状态、作者等条件进行筛选
     * 只返回已发布的文章，不包含用户交互状态
     *
     * @param req 查询请求参数，包含分页信息和筛选条件
     * @return 分页结果，包含文章列表和分页信息，不包含点赞收藏状态
     * @author 刘鑫
     * @date 2025-01-30
     */
    public PageResp<PostListResp> getPostList(PostQueryReq req) {
        return getPostList(req, null);
    }

    /**
     * 分页查询文章列表（支持用户状态）
     * 支持按分类、标签、关键词、状态、作者等条件进行筛选，同时返回当前用户的点赞收藏状态
     * 返回已发布的文章，包含用户的点赞收藏状态
     *
     * @param req    查询请求参数，包含分页信息和筛选条件
     * @param userId 当前用户ID，用于查询点赞收藏状态，可为null
     * @return 分页结果，包含文章列表和分页信息，文章包含用户状态信息
     * @author 刘鑫
     * @date 2025-01-30
     */
    public PageResp<PostListResp> getPostList(PostQueryReq req, Long userId) {
        // 创建分页对象
        Page<PostListResp> page = new Page<>(req.getPage(), req.getSize());

        // 处理搜索关键词
        String keyword = StringUtils.hasText(req.getKeyword()) ? req.getKeyword().trim() : null;

        // 执行分页查询，直接返回PostListResl
        IPage<PostListResp> result = postsMapper.selectPostListResl(page, req.getCategoryId(), req.getTagId(), keyword,
                req.getStatus(), req.getAuthorId(), userId);

        // 使用MyBatis-Plus自动统计的总数
        return new PageResp<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 根据ID查询文章详情（公开接口）
     * 查询文章详细信息并自动增加访问量，不包含用户交互状态
     *
     * @param id 文章ID
     * @return 文章详情信息，包含内容、作者、标签、统计数据等，不包含用户的点赞收藏状态
     * @throws BusinessException 当文章不存在时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    public PostDetailResp getPostDetail(Long id) {
        return getPostDetail(id, null);
    }

    /**
     * 根据ID查询文章详情（包含用户状态）
     * 查询文章详细信息并自动增加访问量，同时返回当前用户的点赞收藏状态
     *
     * @param id     文章ID
     * @param userId 当前用户ID，用于查询点赞收藏状态，可为null
     * @return 文章详情信息，包含内容、作者、标签、统计数据和用户状态
     * @throws BusinessException 当文章不存在时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    public PostDetailResp getPostDetail(Long id, Long userId) {
        PostDetailResp postDetail = postsMapper.selectPostDetailResl(id, userId);
        if (postDetail == null) {
            return null;
        }

        // 附件列表（公开，不限制上传者）
        List<java.util.Map<String, Object>> list = postAttachmentsMapper.selectPostAttachmentsPublic(id);
        if (list != null && !list.isEmpty()) {
            List<PostDetailResp.AttachmentInfo> attachments = list.stream().map(map -> {
                PostDetailResp.AttachmentInfo a = new PostDetailResp.AttachmentInfo();
                Object v;
                v = map.get("attachmentId"); if (v != null) a.setAttachmentId(((Number) v).longValue());
                v = map.get("resourceId"); if (v != null) a.setResourceId(((Number) v).longValue());
                v = map.get("fileName"); if (v != null) a.setFileName(String.valueOf(v));
                v = map.get("pointsNeeded"); if (v != null) a.setPointsNeeded(((Number) v).intValue());
                v = map.get("createdTime"); if (v instanceof java.util.Date) a.setCreatedTime((java.util.Date) v);

                // 根据积分需求和用户购买状态控制文件URL可见性
                Long resourceId = a.getResourceId();
                Integer pointsNeeded = a.getPointsNeeded();
                boolean purchased = false;
                if (pointsNeeded == null || pointsNeeded == 0) {
                    purchased = true; // 免费资源视为已购买
                } else if (userId != null) {
                    purchased = resourceDownloadService.hasUserPurchased(userId, resourceId);
                }
                a.setPurchased(purchased);

                if (pointsNeeded != null && pointsNeeded > 0) {
                    // 积分资源：仅在已购买时返回URL，未登录或未购买一律隐藏URL
                    if (purchased) {
                        v = map.get("fileUrl"); if (v != null) a.setFileUrl(String.valueOf(v));
                    } else {
                        a.setFileUrl(null);
                    }
                } else {
                    // 免费资源：始终返回URL
                    v = map.get("fileUrl"); if (v != null) a.setFileUrl(String.valueOf(v));
                }

                return a;
            }).collect(Collectors.toList());
            postDetail.setAttachments(attachments);
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
     * 管理端查询文章详情（不增加访问量）
     * 查询文章详细信息，包含完整的关联数据，但不增加访问量
     *
     * @param id 文章ID
     * @return 文章详情信息，包含内容、作者、标签、统计数据等
     * @throws BusinessException 当文章不存在时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
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
                return a;
            }).collect(Collectors.toList());
            postDetail.setAttachments(attachments);
        }
        return postDetail;
    }

    /**
     * 点赞文章（已废弃，请使用toggleLike方法）
     *
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
     * 如果用户未点赞则点赞，如果已点赞则取消点赞，同时更新文章点赞数
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 点赞后的状态（true=已点赞，false=已取消点赞）
     * @throws BusinessException 当文章不存在时抛出异常
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
     * 如果用户未收藏则收藏，如果已收藏则取消收藏，同时更新文章收藏数
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 收藏后的状态（true=已收藏，false=已取消收藏）
     * @throws BusinessException 当文章不存在时抛出异常
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
     * 根据点赞数、评论数、访问量等综合指标排序，支持缓存
     *
     * @param limit 限制数量，最多返回的文章数
     * @return 热门文章列表，按热度降序排列
     */
    @Cacheable(value = "hotPosts", key = "#limit", unless = "#result == null || #result.isEmpty()")
    public List<PostListResp> getHotPosts(Integer limit) {
        return getHotPosts(limit, null);
    }

    /**
     * 查询热门文章（支持用户状态）
     * 根据点赞数、评论数、访问量等综合指标排序，同时返回用户点赞收藏状态
     *
     * @param limit  限制数量，最多返回的文章数
     * @param userId 当前用户ID，用于查询点赞收藏状态，可为null
     * @return 热门文章列表，按热度降序排列，包含用户状态信息
     */
    public List<PostListResp> getHotPosts(Integer limit, Long userId) {
        return postsMapper.selectHotPostListResl(limit, userId);
    }

    /**
     * 查询最新文章
     * 按发布时间降序排列，支持缓存
     *
     * @param limit 限制数量，最多返回的文章数
     * @return 最新文章列表，按发布时间降序排列
     */
    @Cacheable(value = "latestPosts", key = "#limit", unless = "#result == null || #result.isEmpty()")
    public List<PostListResp> getLatestPosts(Integer limit) {
        return getLatestPosts(limit, null);
    }

    /**
     * 查询最新文章（支持用户状态）
     * 按发布时间降序排列，同时返回用户点赞收藏状态
     *
     * @param limit  限制数量，最多返回的文章数
     * @param userId 当前用户ID，用于查询点赞收藏状态，可为null
     * @return 最新文章列表，按发布时间降序排列，包含用户状态信息
     */
    public List<PostListResp> getLatestPosts(Integer limit, Long userId) {
        return postsMapper.selectLatestPostListResl(limit, userId);
    }

    /**
     * 创建文章
     * 创建新文章并处理标签关联，支持草稿和发布状态
     *
     * @param req      创建请求，包含文章标题、内容、分类、标签等信息
     * @param authorId 作者ID
     * @return 文章创建响应，包含文章ID、标题、状态和创建时间
     * @throws BusinessException 当文章创建失败时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts" }, allEntries = true)
    public PostCreateResp createPost(PostCreateReq req, Long authorId) {
        // 创建文章对象
        Posts post = new Posts();
        BeanUtils.copyProperties(req, post);
        post.setAuthorId(authorId);
        post.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "draft");
        post.setCreatedAt(new Date());
        post.setUpdatedAt(new Date());
        post.setCreatedBy(authorId);
        post.setUpdatedBy(authorId);
        // 设置默认计数值
        if (post.getViewCount() == null) {
            post.setViewCount(0);
        }
        if (post.getLikeCount() == null) {
            post.setLikeCount(0);
        }

        // 保存文章
        boolean saved = this.save(post);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文章创建失败");
        }

        // 处理标签关联
        if (req.getTagIds() != null && !req.getTagIds().isEmpty()) {
            savePostTags(post.getId(), req.getTagIds());
        }

        // 绑定草稿附件到文章
        if (StringUtils.hasText(req.getDraftKey())) {
            int bindCount = postAttachmentsMapper.bindDraftToPost(req.getDraftKey(), post.getId());
            log.info("绑定草稿附件到文章 - 文章ID: {}, 草稿键: {}, 绑定数量: {}",
                    post.getId(), req.getDraftKey(), bindCount);
        }

        // 构建响应对象
        PostCreateResp response = new PostCreateResp();
        response.setId(post.getId());
        response.setTitle(post.getTitle());
        response.setStatus(post.getStatus());
        response.setCreatedAt(post.getCreatedAt());

        return response;
    }

    /**
     * 更新文章
     * 更新文章信息和标签关联，只有作者本人可以编辑
     *
     * @param req      更新请求，包含文章ID和需要更新的字段
     * @param authorId 作者ID，用于权限验证
     * @return 是否更新成功
     * @throws BusinessException 当文章不存在或无权限时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts" }, allEntries = true)
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

        // 绑定草稿附件到文章（编辑模式下上传的新附件）
        if (org.springframework.util.StringUtils.hasText(req.getDraftKey())) {
            int bindCount = postAttachmentsMapper.bindDraftToPost(req.getDraftKey(), req.getId());
            log.info("编辑时绑定草稿附件到文章 - 文章ID: {}, 草稿键: {}, 绑定数量: {}",
                    req.getId(), req.getDraftKey(), bindCount);
        }

        return true;
    }

    /**
     * 删除文章（软删除）
     * 软删除文章，只有作者本人可以删除，不会物理删除数据
     *
     * @param id       文章ID
     * @param authorId 作者ID，用于权限验证
     * @return 是否删除成功
     * @throws BusinessException 当文章不存在或无权限时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts" }, allEntries = true)
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

        // 删除文章与标签的关联关系（不删除标签本身）
        postTagsMapper.deleteByPostId(id);

        // 软删除点赞记录
        LambdaUpdateWrapper<PostLikes> likeUpdateWrapper = new LambdaUpdateWrapper<>();
        likeUpdateWrapper.eq(PostLikes::getPostId, id)
                .set(PostLikes::getDeletedAt, new Date());
        postLikesMapper.update(null, likeUpdateWrapper);

        // 软删除收藏记录
        LambdaUpdateWrapper<PostFavorites> favoriteUpdateWrapper = new LambdaUpdateWrapper<>();
        favoriteUpdateWrapper.eq(PostFavorites::getPostId, id)
                .set(PostFavorites::getDeletedAt, new Date());
        postFavoritesMapper.update(null, favoriteUpdateWrapper);

        // 软删除文章
        int result = postsMapper.deleteById(id, new Date(), authorId);
        return result > 0;
    }

    /**
     * 发布文章
     * 将草稿状态的文章发布为公开状态，只有作者本人可以操作
     *
     * @param id       文章ID
     * @param authorId 作者ID，用于权限验证
     * @return 是否发布成功
     * @throws BusinessException 当文章不存在或无权限时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts" }, allEntries = true)
    public boolean publishPost(Long id, Long authorId) {
        return updatePostStatus(id, "published", authorId);
    }

    /**
     * 取消发布文章
     * 将已发布的文章改为草稿状态，只有作者本人可以操作
     *
     * @param id       文章ID
     * @param authorId 作者ID，用于权限验证
     * @return 是否操作成功
     * @throws BusinessException 当文章不存在或无权限时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean unpublishPost(Long id, Long authorId) {
        return updatePostStatus(id, "draft", authorId);
    }

    /**
     * 更新文章状态（私有方法）
     * 内部方法，用于统一处理文章状态更新逻辑
     *
     * @param id       文章ID
     * @param status   新状态（draft/published等）
     * @param authorId 作者ID，用于权限验证
     * @return 是否更新成功
     * @throws BusinessException 当文章不存在或无权限时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
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
     * 保存文章标签关联（私有方法）
     * 批量创建文章与标签的关联关系
     *
     * @param postId 文章ID
     * @param tagIds 标签ID列表，为空时不执行任何操作
     * @author 刘鑫
     * @date 2025-01-30
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
     * 更新文章标签关联（私有方法）
     * 先删除原有关联，再创建新的关联关系
     *
     * @param postId 文章ID
     * @param tagIds 新的标签ID列表，为空时只删除原有关联
     * @author 刘鑫
     * @date 2025-01-30
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
     * 统计指定用户已发布状态的文章总数
     *
     * @param userId 用户ID
     * @return 已发布文章数量
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Integer countPublishedPostsByUserId(Long userId) {
        return postsMapper.countPostsByUserIdAndStatus(userId, "published");
    }

    /**
     * 统计用户草稿数量
     * 统计指定用户草稿状态的文章总数
     *
     * @param userId 用户ID
     * @return 草稿文章数量
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Integer countDraftsByUserId(Long userId) {
        return postsMapper.countPostsByUserIdAndStatus(userId, "draft");
    }

    /**
     * 获取用户最后发文时间
     * 获取指定用户最近一次发布文章的时间
     *
     * @param userId 用户ID
     * @return 最后发文时间，如果用户没有发布过文章则返回null
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Date getLastPostTimeByUserId(Long userId) {
        return postsMapper.getLastPostTimeByUserId(userId);
    }

    /**
     * 统计用户文章数量（按状态）
     * 统计指定用户在指定状态下的文章总数
     *
     * @param userId 用户ID
     * @param status 文章状态（draft/published等）
     * @return 指定状态的文章数量
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Integer countPostsByUserId(Long userId, String status) {
        return postsMapper.countPostsByUserIdAndStatus(userId, status);
    }

    /**
     * 统计全站已发布文章数量
     * 统计整个网站所有已发布状态的文章总数
     *
     * @return 全站已发布文章数量
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Integer countAllPublishedPosts() {
        return postsMapper.countPublishedPosts();
    }

    /**
     * 统计全站文章总浏览量
     * 统计整个网站所有文章的浏览量总和
     *
     * @return 全站文章总浏览量
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Long countAllViews() {
        return postsMapper.countAllViews();
    }

    /**
     * 统计用户所有文章的浏览量总和
     * 统计指定用户所有文章的浏览量累计总数
     *
     * @param userId 用户ID
     * @return 用户文章总浏览量
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Long countViewsByUserId(Long userId) {
        return postsMapper.countViewsByUserId(userId);
    }

    /**
     * 管理端分页查询文章列表
     * 支持按标题、分类、状态、作者等条件进行筛选，管理员可查看所有状态的文章
     *
     * @param page       页码，从1开始
     * @param size       每页大小，建议10-50之间
     * @param title      文章标题（可选，模糊搜索）
     * @param categoryId 分类ID（可选，筛选指定分类）
     * @param status     文章状态（可选，draft/published等）
     * @param authorId   作者ID（可选，筛选指定作者）
     * @param includeDeleted 是否包含已删除文章
     * @return 分页结果，包含文章列表和分页信息
     * @author 刘鑫
     * @date 2025-01-30
     */
    public PageResp<PostListResp> getPostListForAdmin(int page, int size, String title, Long categoryId, String status,
                                                      Long authorId, Boolean includeDeleted) {
        log.info("管理端查询文章列表 - 页码: {}, 每页: {}, 标题: {}, 分类: {}, 状态: {}, 作者: {}, 包含已删除: {}",
                page, size, title, categoryId, status, authorId, includeDeleted);

        try {
            // 创建分页对象
            Page<PostListResp> pageObj = new Page<>(page, size);

            // 处理搜索关键词
            String keyword = StringUtils.hasText(title) ? title.trim() : null;

            // 执行分页查询，传递includeDeleted参数
            IPage<PostListResp> result = postsMapper.selectPostListForAdmin(pageObj, categoryId, keyword, status,
                    authorId, includeDeleted);

            log.info("管理端文章列表查询成功 - 总数: {}, 当前页数据: {}", result.getTotal(), result.getRecords().size());

            // 使用MyBatis-Plus自动统计的总数
            return new PageResp<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());

        } catch (Exception e) {
            log.error("管理端文章列表查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询文章列表失败: " + e.getMessage());
        }
    }

    /**
     * 管理端更新文章状态
     * 管理员可以修改任何文章的状态，支持发布、下架、删除等操作
     *
     * @param id         文章ID
     * @param status     新状态（draft/published/deleted等）
     * @param operatorId 操作者ID，用于记录操作日志
     * @return 是否更新成功
     * @throws BusinessException 当文章不存在时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts" }, allEntries = true)
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
     * 管理员可以删除任何文章，执行软删除操作，不会物理删除数据
     *
     * @param id         文章ID
     * @param operatorId 操作者ID，用于记录操作日志
     * @return 是否删除成功
     * @throws BusinessException 当文章不存在时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts" }, allEntries = true)
    public boolean deletePostForAdmin(Long id, Long operatorId) {
        log.info("管理端删除文章 - 文章ID: {}, 操作者: {}", id, operatorId);

        try {
            // 检查文章是否存在
            Posts existPost = this.getById(id);
            if (existPost == null || existPost.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
            }

            // 删除文章与标签的关联关系（不删除标签本身）
            postTagsMapper.deleteByPostId(id);

            // 软删除点赞记录
            LambdaUpdateWrapper<PostLikes> likeUpdateWrapper = new LambdaUpdateWrapper<>();
            likeUpdateWrapper.eq(PostLikes::getPostId, id)
                    .set(PostLikes::getDeletedAt, new Date());
            postLikesMapper.update(null, likeUpdateWrapper);

            // 软删除收藏记录
            LambdaUpdateWrapper<PostFavorites> favoriteUpdateWrapper = new LambdaUpdateWrapper<>();
            favoriteUpdateWrapper.eq(PostFavorites::getPostId, id)
                    .set(PostFavorites::getDeletedAt, new Date());
            postFavoritesMapper.update(null, favoriteUpdateWrapper);

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
     * 管理员可以批量修改多篇文章的状态，提高管理效率
     *
     * @param ids    文章ID列表，不能为空
     * @param status 新状态（draft/published/deleted等）
     * @return 是否批量更新成功
     * @throws BusinessException 当参数无效时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts" }, allEntries = true)
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
     * 管理员可以批量删除多篇文章，执行软删除操作，同时删除相关的点赞、收藏、评论和标签关联
     *
     * @param ids 文章ID列表，不能为空
     * @return 是否批量删除成功
     * @throws BusinessException 当参数无效时抛出异常
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts" }, allEntries = true)
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
                    .set(PostLikes::getDeletedAt, new Date());
            postLikesMapper.update(null, likesUpdateWrapper);

            // 软删除收藏记录
            LambdaUpdateWrapper<PostFavorites> favoritesUpdateWrapper = new LambdaUpdateWrapper<>();
            favoritesUpdateWrapper.in(PostFavorites::getPostId, ids)
                    .set(PostFavorites::getDeletedAt, new Date());
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

    /**
     * 恢复已删除的文章
     * 将软删除的文章恢复为正常状态
     *
     * @param id 文章ID
     * @return 是否恢复成功
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts" }, allEntries = true)
    public boolean restorePost(Long id) {
        try {
            if (id == null) {
                return false;
            }

            // 使用原生SQL恢复文章，绕过MyBatis-Plus的逻辑删除限制
            int result = postsMapper.restorePostById(id);

            log.info("恢复文章ID: {}, 结果: {}", id, result > 0 ? "成功" : "失败");
            return result > 0;
        } catch (Exception e) {
            log.error("恢复文章失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 彻底删除文章（物理删除）
     * @param id 文章ID
     * @param updatedBy 操作者ID
     */
    @Transactional
    public void permanentDeletePost(Long id, Long updatedBy) {
        if (id == null) {
            throw new IllegalArgumentException("文章ID不能为空");
        }

        try {
            // 删除文章的所有关联数据
            // 删除文章收藏记录
            postFavoritesMapper.deleteByPostId(id);
            // 删除文章点赞记录
            postLikesMapper.deleteByPostId(id);
            // 删除文章评论
            // commentsMapper.deleteByPostId(id);
            commentsMapper.deleteChildrenByPostId(id);
            // 删除顶级评论
            commentsMapper.deleteRootsByPostId(id);
            // 删除文章标签关联
            postTagsMapper.deleteByPostId(id);
            // 删除文章附件关联
            postAttachmentsMapper.deleteByPostId(id);

            // 物理删除文章
            int result = postsMapper.permanentDeleteById(id);
            if (result <= 0) {
                throw new RuntimeException("文章删除失败，可能文章不存在");
            }

            log.info("彻底删除文章成功，文章ID: {}, 操作者: {}", id, updatedBy);
        } catch (Exception e) {
            log.error("彻底删除文章失败，文章ID: {}, 操作者: {}, 错误: {}", id, updatedBy, e.getMessage(), e);
            throw new RuntimeException("彻底删除文章失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量彻底删除文章（物理删除）
     * @param ids 文章ID列表
     * @param updatedBy 操作者ID
     */
    @Transactional
    public void batchPermanentDeletePosts(java.util.List<Long> ids, Long updatedBy) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("文章ID列表不能为空");
        }

        try {
            // 删除文章的所有关联数据（按正确顺序）
            for (Long postId : ids) {
                // 删除文章收藏记录
                postFavoritesMapper.deleteByPostId(postId);
                // 删除文章点赞记录
                postLikesMapper.deleteByPostId(postId);
                // 删除文章评论
                // commentsMapper.deleteByPostId(postId);
                commentsMapper.deleteChildrenByPostId(postId);
                // 删除顶级评论
                commentsMapper.deleteRootsByPostId(postId);
                // 删除文章标签关联
                postTagsMapper.deleteByPostId(postId);
                // 删除文章附件关联
                postAttachmentsMapper.deleteByPostId(postId);
            }

            // 批量物理删除文章
            postsMapper.permanentDeleteByIds(ids);

            log.info("批量彻底删除文章成功，文章ID: {}, 操作者: {}", ids, updatedBy);
        } catch (Exception e) {
            log.error("批量彻底删除文章失败，文章ID: {}, 操作者: {}, 错误: {}", ids, updatedBy, e.getMessage(), e);
            throw new RuntimeException("批量彻底删除文章失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public boolean permanentDeletePost(Long id) {
        try {
            this.permanentDeletePost(id, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public boolean batchPermanentDeletePosts(java.util.List<Long> ids) {
        try {
            this.batchPermanentDeletePosts(ids, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取用户收藏的文章列表
     * @param req 查询请求参数
     * @param userId 用户ID
     * @return 分页的文章列表
     * @author 刘鑫
     * @date 2025-09-26T00:20:02+08:00
     */
    public PageResp<PostListResp> getFavoritePosts(PostQueryReq req, Long userId) {
        // 创建分页对象
        Page<PostListResp> page = new Page<>(req.getPage(), req.getSize());

        // 处理搜索关键词
        String keyword = StringUtils.hasText(req.getKeyword()) ? req.getKeyword().trim() : null;

        // 执行分页查询，查询用户收藏的文章
        IPage<PostListResp> result = postsMapper.selectFavoritePostList(page, userId, keyword);

        // 使用MyBatis-Plus自动统计的总数
        return new PageResp<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
