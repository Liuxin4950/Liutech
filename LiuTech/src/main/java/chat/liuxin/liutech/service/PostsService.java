package chat.liuxin.liutech.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.PostTagsMapper;
import chat.liuxin.liutech.mapper.TagsMapper;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.model.PostTags;
import chat.liuxin.liutech.model.Tags;
import chat.liuxin.liutech.req.PostCreateReq;
import chat.liuxin.liutech.req.PostQueryReq;
import chat.liuxin.liutech.req.PostUpdateReq;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.resl.PostDetailResl;
import chat.liuxin.liutech.resl.PostListResl;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.BusinessException;

import org.springframework.transaction.annotation.Transactional;
import java.util.Date;

/**
 * 文章服务类
 */
@Service
public class PostsService extends ServiceImpl<PostsMapper, Posts> {

    @Autowired
    private PostsMapper postsMapper;
    
    @Autowired
    private PostTagsMapper postTagsMapper;
    
    @Autowired
    private TagsMapper tagsMapper;

    /**
     * 分页查询文章列表
     * @param req 查询请求
     * @return 分页结果
     */
    public PageResl<PostListResl> getPostList(PostQueryReq req) {
        // 创建分页对象
        Page<Posts> page = new Page<>(req.getPage(), req.getSize());
        
        // 处理搜索关键词
        String keyword = StringUtils.hasText(req.getKeyword()) ? req.getKeyword().trim() : null;
        
        // 执行分页查询，MyBatis-Plus会自动处理count查询
        IPage<Posts> result = postsMapper.selectPostsWithDetails(page, req.getCategoryId(), req.getTagId(), keyword);
        
        // 为每篇文章查询标签信息
        List<Posts> posts = result.getRecords();
        if (!posts.isEmpty()) {
            // 为每篇文章查询标签信息
            for (Posts post : posts) {
                List<Tags> tags = tagsMapper.selectTagsByPostId(post.getId());
                post.setTags(tags);
            }
        }
        
        // 转换为响应对象
        List<PostListResl> postList = result.getRecords().stream()
                .map(this::convertToPostListResl)
                .collect(Collectors.toList());
        
        // 使用MyBatis-Plus自动统计的总数
        return new PageResl<>(postList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 根据ID查询文章详情
     * @param id 文章ID
     * @return 文章详情
     */
    public PostDetailResl getPostDetail(Long id) {
        Posts post = postsMapper.selectPostWithDetails(id);
        if (post == null) {
            return null;
        }
        return convertToPostDetailResl(post);
    }

    /**
     * 查询热门文章
     * @param limit 限制数量
     * @return 热门文章列表
     */
    public List<PostListResl> getHotPosts(Integer limit) {
        List<Posts> posts = postsMapper.selectHotPosts(limit);
        return posts.stream()
                .map(this::convertToPostListResl)
                .collect(Collectors.toList());
    }

    /**
     * 查询最新文章
     * @param limit 限制数量
     * @return 最新文章列表
     */
    public List<PostListResl> getLatestPosts(Integer limit) {
        List<Posts> posts = postsMapper.selectLatestPosts(limit);
        return posts.stream()
                .map(this::convertToPostListResl)
                .collect(Collectors.toList());
    }

    /**
     * 转换为文章列表响应对象
     */
    private PostListResl convertToPostListResl(Posts post) {
        PostListResl resl = new PostListResl();
        BeanUtils.copyProperties(post, resl);
        
        // 设置分类信息
        if (post.getCategory() != null) {
            PostListResl.CategoryInfo categoryInfo = new PostListResl.CategoryInfo();
            categoryInfo.setId(post.getCategory().getId());
            categoryInfo.setName(post.getCategory().getName());
            resl.setCategory(categoryInfo);
        }
        
        // 设置作者信息
        if (post.getAuthor() != null) {
            PostListResl.AuthorInfo authorInfo = new PostListResl.AuthorInfo();
            authorInfo.setId(post.getAuthor().getId());
            authorInfo.setUsername(post.getAuthor().getUsername());
            authorInfo.setAvatarUrl(post.getAuthor().getAvatarUrl());
            resl.setAuthor(authorInfo);
        }
        
        // 设置标签信息
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            List<PostListResl.TagInfo> tagInfos = post.getTags().stream()
                    .map(tag -> {
                        PostListResl.TagInfo tagInfo = new PostListResl.TagInfo();
                        tagInfo.setId(tag.getId());
                        tagInfo.setName(tag.getName());
                        return tagInfo;
                    })
                    .collect(Collectors.toList());
            resl.setTags(tagInfos);
        } else {
            resl.setTags(new ArrayList<>());
        }
        
        return resl;
    }

    /**
     * 转换为文章详情响应对象
     */
    private PostDetailResl convertToPostDetailResl(Posts post) {
        PostDetailResl resl = new PostDetailResl();
        BeanUtils.copyProperties(post, resl);
        
        // 设置分类信息
        if (post.getCategory() != null) {
            PostDetailResl.CategoryInfo categoryInfo = new PostDetailResl.CategoryInfo();
            categoryInfo.setId(post.getCategory().getId());
            categoryInfo.setName(post.getCategory().getName());
            categoryInfo.setDescription(post.getCategory().getDescription());
            resl.setCategory(categoryInfo);
        }
        
        // 设置作者信息
        if (post.getAuthor() != null) {
            PostDetailResl.AuthorInfo authorInfo = new PostDetailResl.AuthorInfo();
            authorInfo.setId(post.getAuthor().getId());
            authorInfo.setUsername(post.getAuthor().getUsername());
            authorInfo.setAvatarUrl(post.getAuthor().getAvatarUrl());
            resl.setAuthor(authorInfo);
        }
        
        // 设置标签信息
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            List<PostDetailResl.TagInfo> tagInfos = post.getTags().stream()
                    .map(tag -> {
                        PostDetailResl.TagInfo tagInfo = new PostDetailResl.TagInfo();
                        tagInfo.setId(tag.getId());
                        tagInfo.setName(tag.getName());
                        return tagInfo;
                    })
                    .collect(Collectors.toList());
            resl.setTags(tagInfos);
        } else {
            resl.setTags(new ArrayList<>());
        }
        
        return resl;
    }

    /**
     * 创建文章
     * @param req 创建请求
     * @param authorId 作者ID
     * @return 文章ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPost(PostCreateReq req, Long authorId) {
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
        
        return post.getId();
    }

    /**
     * 更新文章
     * @param req 更新请求
     * @param authorId 作者ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
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
}