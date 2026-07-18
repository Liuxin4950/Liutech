package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

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
import chat.liuxin.liutech.utils.FileUtil;

/**
 * 文章服务类
 * 提供文章的增删改查、点赞收藏、统计等核心业务功能
 *
 * @author LiuTech
 * @date 2025-08-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostsService extends ServiceImpl<PostsMapper, Posts> {

    private static final String PUBLIC_SITE_BASE_URL = "https://liuxin.chat";

    private final PostsMapper postsMapper;

    private final PostTagsMapper postTagsMapper;

    private final PostLikesMapper postLikesMapper;

    private final PostFavoritesMapper postFavoritesMapper;

    private final PostAttachmentsMapper postAttachmentsMapper;

    private final ResourceDownloadService resourceDownloadService;

    private final FileUtil fileUtil;

    private final ImagesService imagesService;

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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    @Cacheable(value = "postList", key = "#req.page + ':' + #req.size + ':' + #req.categoryId + ':' + #req.tagId + ':' + #req.keyword + ':' + #req.status + ':' + #req.authorId + ':' + #req.seriesId + ':' + #userId", unless = "#result == null")
    public PageResp<PostListResp> getPostList(PostQueryReq req, Long userId) {
        // 创建分页对象
        Page<PostListResp> page = new Page<>(req.getPage(), req.getSize());

        // 处理搜索关键词
        String keyword = StringUtils.hasText(req.getKeyword()) ? req.getKeyword().trim() : null;

        // 执行分页查询，直接返回PostListResl
        IPage<PostListResp> result = postsMapper.selectPostListResl(page, req.getCategoryId(), req.getTagId(), keyword,
                req.getStatus(), req.getAuthorId(), req.getSeriesId(), userId);

        // 批量加载标签（替代N+1嵌套查询）
        fillTags(result.getRecords());
        result.getRecords().forEach(this::normalizePostListUrls);
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
        if (!canViewPostDetail(postDetail, userId)) {
            log.warn("拒绝访问未发布文章详情 - 文章ID: {}, 用户ID: {}, 状态: {}", id, userId, postDetail.getStatus());
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
                v = map.get("resourceType"); if (v != null) a.setResourceType(String.valueOf(v));
                v = map.get("downloadType");
                Integer downloadType = v != null ? ((Number) v).intValue() : null;

                // 根据下载类型、积分需求和购买状态控制付费资源敏感字段可见性
                Long resourceId = a.getResourceId();
                Integer pointsNeeded = a.getPointsNeeded();
                boolean paidResource = (downloadType != null && downloadType == 1)
                        || (pointsNeeded != null && pointsNeeded > 0);
                boolean purchased = !paidResource;
                if (paidResource && userId != null && resourceId != null) {
                    purchased = resourceDownloadService.hasUserPurchased(userId, resourceId);
                }
                a.setPurchased(purchased);

                if (purchased) {
                    v = map.get("fileUrl"); if (v != null) a.setFileUrl(String.valueOf(v));
                    v = map.get("externalLink"); if (v != null) a.setExternalLink(String.valueOf(v));
                    v = map.get("purchasedNote"); if (v != null) a.setPurchasedNote(String.valueOf(v));
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

        fillSeriesCatalog(postDetail);
        normalizePostDetailUrls(postDetail);
        return postDetail;
    }

    private boolean canViewPostDetail(PostDetailResp postDetail, Long userId) {
        if ("published".equals(postDetail.getStatus())) {
            return true;
        }
        return userId != null && postDetail.getAuthorId() != null && postDetail.getAuthorId().equals(userId);
    }

    /**
     * 查询热门文章
     * 根据点赞数、评论数、访问量等综合指标排序，支持缓存
     *
     * @param limit 限制数量，最多返回的文章数
     * @return 热门文章列表，按热度降序排列
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<PostListResp> getHotPosts(Integer limit, Long userId) {
        List<PostListResp> posts = postsMapper.selectHotPostListResl(limit, userId);
        fillTags(posts);
        posts.forEach(this::normalizePostListUrls);
        return posts;
    }

    /**
     * 批量加载标签并合并到文章列表（替代 N+1 嵌套查询）
     */
    void fillTags(List<PostListResp> posts) {
        if (posts == null || posts.isEmpty()) return;
        List<Long> postIds = posts.stream().map(PostListResp::getId).toList();
        List<Map<String, Object>> tagRows = postsMapper.selectTagsByPostIds(postIds);
        Map<Long, List<PostListResp.TagInfo>> tagMap = tagRows.stream()
                .collect(Collectors.groupingBy(
                        row -> ((Number) row.get("postId")).longValue(),
                        Collectors.mapping(
                                row -> {
                                    PostListResp.TagInfo tag = new PostListResp.TagInfo();
                                    tag.setId(((Number) row.get("id")).longValue());
                                    tag.setName((String) row.get("name"));
                                    return tag;
                                },
                                Collectors.toList()
                        )
                ));
        posts.forEach(post -> post.setTags(
                tagMap.getOrDefault(post.getId(), List.of())
        ));
    }

    /**
     * 填充系列目录（文章详情页系列导航用）
     * 仅当文章属于某系列时，查询同系列已发布文章并标记当前篇。
     */
    void fillSeriesCatalog(PostDetailResp postDetail) {
        if (postDetail == null || postDetail.getSeries() == null || postDetail.getSeries().getId() == null) {
            return;
        }
        List<Posts> catalog = postsMapper.selectSeriesPostCatalog(postDetail.getSeries().getId());
        List<PostDetailResp.SeriesCatalogItem> items = catalog.stream().map(p -> {
            PostDetailResp.SeriesCatalogItem item = new PostDetailResp.SeriesCatalogItem();
            item.setId(p.getId());
            item.setTitle(p.getTitle());
            item.setSort(p.getSeriesSort());
            item.setCurrent(p.getId().equals(postDetail.getId()));
            return item;
        }).collect(Collectors.toList());
        postDetail.setSeriesCatalog(items);
    }

    /**
     * 更新文章的系列归属与排序
     * 单独用 UpdateWrapper 显式 set，绕过 MyBatis-Plus 默认不更新 null 字段的策略，
     * 使 seriesId 传 null 时能把文章移出系列。
     */
    private void updateSeriesAssignment(Long postId, Long seriesId, Integer seriesSort) {
        LambdaUpdateWrapper<Posts> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Posts::getId, postId)
                .set(Posts::getSeriesId, seriesId)
                .set(Posts::getSeriesSort, seriesSort != null ? seriesSort : 0);
        this.update(wrapper);
    }

    void normalizePostListUrls(PostListResp post) {
        if (post == null) {
            return;
        }

        post.setCoverImage(normalizePublicUrl(post.getCoverImage()));
        post.setThumbnail(normalizePublicUrl(post.getThumbnail()));

        if (post.getAuthor() != null) {
            post.getAuthor().setAvatarUrl(normalizePublicUrl(post.getAuthor().getAvatarUrl()));
        }
    }

    void normalizePostDetailUrls(PostDetailResp post) {
        if (post == null) {
            return;
        }

        post.setCoverImage(normalizePublicUrl(post.getCoverImage()));
        post.setThumbnail(normalizePublicUrl(post.getThumbnail()));
        post.setContent(normalizePublicUrl(post.getContent()));

        if (post.getAuthor() != null) {
            post.getAuthor().setAvatarUrl(normalizePublicUrl(post.getAuthor().getAvatarUrl()));
        }

        if (post.getAttachments() != null) {
            post.getAttachments().forEach(attachment -> {
                attachment.setFileUrl(normalizePublicUrl(attachment.getFileUrl()));
                attachment.setExternalLink(normalizePublicUrl(attachment.getExternalLink()));
            });
        }
    }

    private String normalizePublicUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        return value
                .replace("http://liuxin.chat", PUBLIC_SITE_BASE_URL)
                .replace("http://liutech.chat", PUBLIC_SITE_BASE_URL)
                .replace("https://liutech.chat", PUBLIC_SITE_BASE_URL);
    }

    /**
     * 查询最新文章
     * 按发布时间降序排列，支持缓存
     *
     * @param limit 限制数量，最多返回的文章数
     * @return 最新文章列表，按发布时间降序排列
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<PostListResp> getLatestPosts(Integer limit, Long userId) {
        List<PostListResp> posts = postsMapper.selectLatestPostListResl(limit, userId);
        fillTags(posts);
        posts.forEach(this::normalizePostListUrls);
        return posts;
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
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "allTags" }, allEntries = true)
    public PostCreateResp createPost(PostCreateReq req, Long authorId) {
        // 创建文章对象
        Posts post = new Posts();
        if (req != null) {
            BeanUtils.copyProperties(req, post);
        }
        post.setAuthorId(authorId);
        post.setStatus(req != null && StringUtils.hasText(req.getStatus()) ? req.getStatus() : "draft");
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
        if (post.getSeriesSort() == null) {
            post.setSeriesSort(0);
        }

        // 保存文章
        boolean saved = this.save(post);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文章创建失败");
        }

        applyImageReferenceDelta(countUrls(collectPostImageUrls(post.getCoverImage(), post.getThumbnail(), post.getContent())));

        // 处理标签关联
        if (req != null && req.getTagIds() != null && !req.getTagIds().isEmpty()) {
            savePostTags(post.getId(), req.getTagIds());
        }

        // 绑定草稿附件到文章
        if (req != null && StringUtils.hasText(req.getDraftKey())) {
            int bindCount = postAttachmentsMapper.bindDraftToPost(req.getDraftKey(), post.getId());
            log.debug("绑定草稿附件到文章 - 文章ID: {}, 草稿键: {}, 绑定数量: {}",
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
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "allTags" }, allEntries = true)
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

        List<String> oldImageUrls = collectPostImageUrls(existPost.getCoverImage(), existPost.getThumbnail(), existPost.getContent());
        List<String> newImageUrls = collectPostImageUrls(req.getCoverImage(), req.getThumbnail(), req.getContent());

        // 更新文章信息
        Posts post = new Posts();
        BeanUtils.copyProperties(req, post);
        post.setUpdatedAt(new Date());
        post.setUpdatedBy(authorId);

        boolean updated = this.updateById(post);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文章更新失败");
        }

        // 系列归属与排序单独更新（允许 seriesId 置空以移出系列）
        updateSeriesAssignment(req.getId(), req.getSeriesId(), req.getSeriesSort());

        syncImageReferences(oldImageUrls, newImageUrls);

        // 更新标签关联
        updatePostTags(req.getId(), req.getTagIds());

        // 绑定草稿附件到文章（编辑模式下上传的新附件）
        if (org.springframework.util.StringUtils.hasText(req.getDraftKey())) {
            int bindCount = postAttachmentsMapper.bindDraftToPost(req.getDraftKey(), req.getId());
            log.debug("编辑时绑定草稿附件到文章 - 文章ID: {}, 草稿键: {}, 绑定数量: {}",
                    req.getId(), req.getDraftKey(), bindCount);
        }

        return true;
    }

    /**
     * 更新文章（管理员版本，跳过作者权限校验）
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "allTags" }, allEntries = true)
    public boolean updatePostForAdmin(PostUpdateReq req, Long operatorId) {
        Posts existPost = this.getById(req.getId());
        if (existPost == null || existPost.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        List<String> oldImageUrls = collectPostImageUrls(existPost.getCoverImage(), existPost.getThumbnail(), existPost.getContent());
        List<String> newImageUrls = collectPostImageUrls(req.getCoverImage(), req.getThumbnail(), req.getContent());

        Posts post = new Posts();
        BeanUtils.copyProperties(req, post);
        post.setUpdatedAt(new Date());
        post.setUpdatedBy(operatorId);

        boolean updated = this.updateById(post);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文章更新失败");
        }

        updateSeriesAssignment(req.getId(), req.getSeriesId(), req.getSeriesSort());

        syncImageReferences(oldImageUrls, newImageUrls);
        updatePostTags(req.getId(), req.getTagIds());

        if (org.springframework.util.StringUtils.hasText(req.getDraftKey())) {
            int bindCount = postAttachmentsMapper.bindDraftToPost(req.getDraftKey(), req.getId());
            log.debug("编辑时绑定草稿附件到文章 - 文章ID: {}, 草稿键: {}, 绑定数量: {}",
                    req.getId(), req.getDraftKey(), bindCount);
        }

        return true;
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

    private Map<String, Integer> countUrls(List<String> urls) {
        Map<String, Integer> counts = new HashMap<>();
        if (urls == null || urls.isEmpty()) {
            return counts;
        }
        for (String url : urls) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            counts.merge(url, 1, (oldValue, delta) ->
                    (oldValue == null ? 0 : oldValue) + (delta == null ? 0 : delta));
        }
        return counts;
    }

    private void syncImageReferences(List<String> oldUrls, List<String> newUrls) {
        Map<String, Integer> oldCounts = countUrls(oldUrls);
        Map<String, Integer> newCounts = countUrls(newUrls);
        Set<String> allUrls = new HashSet<>();
        allUrls.addAll(oldCounts.keySet());
        allUrls.addAll(newCounts.keySet());

        Map<String, Integer> delta = new HashMap<>();
        for (String url : allUrls) {
            int d = newCounts.getOrDefault(url, 0) - oldCounts.getOrDefault(url, 0);
            if (d != 0) {
                delta.put(url, d);
            }
        }
        applyImageReferenceDelta(delta);
    }

    private void applyImageReferenceDelta(Map<String, Integer> deltaByUrl) {
        if (deltaByUrl == null || deltaByUrl.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : deltaByUrl.entrySet()) {
            Integer delta = entry.getValue();
            if (delta == null || delta == 0) {
                continue;
            }
            imagesService.incrementImageUsageCountByUrl(entry.getKey(), delta);
        }
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
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "allTags" }, allEntries = true)
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

        // 软删除不改变 usage_count（引用仍存在，只是标记删除）
        // usage_count 只在物理删除时减少

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
    @CacheEvict(value = { "hotPosts", "latestPosts", "postList", "allTags" }, allEntries = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Long countViewsByUserId(Long userId) {
        return postsMapper.countViewsByUserId(userId);
    }

    /**
     * 获取所有已发布的文章（用于 sitemap 生成）
     * 只返回状态为 "published" 的文章
     *
     * @return 已发布文章列表
     * @author 刘鑫
     * @date 2025-01-18
     */
    @Transactional(readOnly = true)
    public List<Posts> getPublishedPosts() {
        return postsMapper.selectPublishedPosts();
    }

}
