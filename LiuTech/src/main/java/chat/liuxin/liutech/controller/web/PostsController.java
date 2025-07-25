package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.PostCreateReq;
import chat.liuxin.liutech.req.PostQueryReq;
import chat.liuxin.liutech.req.PostUpdateReq;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.resl.PostDetailResl;
import chat.liuxin.liutech.resl.PostListResl;
import chat.liuxin.liutech.service.PostsService;
import lombok.extern.slf4j.Slf4j;

/**
 * 文章控制器
 * 提供文章相关的REST API接口，包括文章列表查询、详情查看、搜索等功能
 * 
 * @author liuxin
 */
@Slf4j
@RestController
@RequestMapping("/posts")
public class PostsController {

    @Autowired
    private PostsService postsService;

    /**
     * 分页查询文章列表
     * 支持按分类、标签、关键词搜索，支持排序
     * 
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @param categoryId 分类ID（可选）
     * @param tagId 标签ID（可选）
     * @param keyword 搜索关键词（可选）
     * @param sort 排序方式（latest: 最新, hot: 热门）
     * @return 分页文章列表
     */
    @GetMapping
    public Result<PageResl<PostListResl>> getPostList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort) {
        
        log.info("查询文章列表 - 页码: {}, 大小: {}, 分类: {}, 标签: {}, 关键词: {}, 排序: {}", 
                page, size, categoryId, tagId, keyword, sort);
        
        PostQueryReq req = new PostQueryReq();
        req.setPage(page);
        req.setSize(size);
        req.setCategoryId(categoryId);
        req.setTagId(tagId);
        req.setKeyword(keyword);
        req.setSort(sort);
        
        PageResl<PostListResl> result = postsService.getPostList(req);
        log.info("查询文章列表成功 - 总数: {}, 当前页: {}", result.getTotal(), result.getCurrent());
        
        return Result.success("查询成功", result);
    }

    /**
     * 根据ID查询文章详情
     * 
     * @param id 文章ID
     * @return 文章详情
     */
    @GetMapping("/{id}")
    public Result<PostDetailResl> getPostDetail(@PathVariable Long id) {
        log.info("查询文章详情 - ID: {}", id);
        
        PostDetailResl post = postsService.getPostDetail(id);
        if (post == null) {
            log.warn("文章不存在 - ID: {}", id);
            return Result.fail(ErrorCode.ARTICLE_NOT_FOUND);
        }
        
        log.info("查询文章详情成功 - 标题: {}", post.getTitle());
        return Result.success("查询成功", post);
    }

    /**
     * 查询热门文章
     * 根据评论数量排序
     * 
     * @param limit 限制数量，默认10
     * @return 热门文章列表
     */
    @GetMapping("/hot")
    public Result<List<PostListResl>> getHotPosts(
            @RequestParam(defaultValue = "10") Integer limit) {
        
        log.info("查询热门文章 - 限制数量: {}", limit);
        
        List<PostListResl> posts = postsService.getHotPosts(limit);
        log.info("查询热门文章成功 - 数量: {}", posts.size());
        
        return Result.success("查询成功", posts);
    }

    /**
     * 查询最新文章
     * 按创建时间倒序排列
     * 
     * @param limit 限制数量，默认10
     * @return 最新文章列表
     */
    @GetMapping("/latest")
    public Result<List<PostListResl>> getLatestPosts(
            @RequestParam(defaultValue = "10") Integer limit) {
        
        log.info("查询最新文章 - 限制数量: {}", limit);
        
        List<PostListResl> posts = postsService.getLatestPosts(limit);
        log.info("查询最新文章成功 - 数量: {}", posts.size());
        
        return Result.success("查询成功", posts);
    }

    /**
     * 搜索文章
     * 根据关键词在标题、内容、摘要中搜索
     * 
     * @param keyword 搜索关键词
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 搜索结果
     */
    @GetMapping("/search")
    public Result<PageResl<PostListResl>> searchPosts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        
        log.info("搜索文章 - 关键词: {}, 页码: {}, 大小: {}", keyword, page, size);
        
        PostQueryReq req = new PostQueryReq();
        req.setPage(page);
        req.setSize(size);
        req.setKeyword(keyword);
        
        PageResl<PostListResl> result = postsService.getPostList(req);
        log.info("搜索文章成功 - 关键词: {}, 总数: {}", keyword, result.getTotal());
        
        return Result.success("搜索成功", result);
    }

    /**
     * 创建文章
     * 需要用户登录，文章作者为当前登录用户
     * 
     * @param req 创建请求
     * @param request HTTP请求对象
     * @return 创建成功的文章ID
     */
    @PostMapping
    public Result<Long> createPost(@Valid @RequestBody PostCreateReq req, HttpServletRequest request) {
        try {
            // 从请求中获取当前用户ID（这里需要根据实际的认证机制获取）
            // 假设通过JWT或Session获取用户ID
            Long authorId = getCurrentUserId(request);
            if (authorId == null) {
                return Result.fail(ErrorCode.NOT_LOGIN_ERROR);
            }
            
            Long postId = postsService.createPost(req, authorId);
            return Result.success(postId);
        } catch (Exception e) {
            log.error("创建文章失败", e);
            return Result.fail(ErrorCode.OPERATION_ERROR, "创建文章失败: " + e.getMessage());
        }
    }

    /**
     * 更新文章
     * 只有文章作者可以更新自己的文章
     * 
     * @param req 更新请求
     * @param request HTTP请求对象
     * @return 更新结果
     */
    @PutMapping
    public Result<Boolean> updatePost(@Valid @RequestBody PostUpdateReq req, HttpServletRequest request) {
        try {
            // 获取当前用户ID
            Long authorId = getCurrentUserId(request);
            if (authorId == null) {
                return Result.fail(ErrorCode.NOT_LOGIN_ERROR);
            }
            
            boolean success = postsService.updatePost(req, authorId);
            return Result.success(success);
        } catch (Exception e) {
            log.error("更新文章失败", e);
            return Result.fail(ErrorCode.OPERATION_ERROR, "更新文章失败: " + e.getMessage());
        }
    }

    /**
     * 删除文章
     * 只有文章作者可以删除自己的文章
     * 
     * @param id 文章ID
     * @param request HTTP请求对象
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deletePost(@PathVariable Long id, HttpServletRequest request) {
        try {
            // 获取当前用户ID
            Long authorId = getCurrentUserId(request);
            if (authorId == null) {
                return Result.fail(ErrorCode.NOT_LOGIN_ERROR);
            }
            
            boolean success = postsService.deletePost(id, authorId);
            return Result.success(success);
        } catch (Exception e) {
            log.error("删除文章失败", e);
            return Result.fail(ErrorCode.OPERATION_ERROR, "删除文章失败: " + e.getMessage());
        }
    }

    /**
     * 发布文章
     * 将草稿状态的文章发布
     * 
     * @param id 文章ID
     * @param request HTTP请求对象
     * @return 发布结果
     */
    @PutMapping("/{id}/publish")
    public Result<Boolean> publishPost(@PathVariable Long id, HttpServletRequest request) {
        try {
            // 获取当前用户ID
            Long authorId = getCurrentUserId(request);
            if (authorId == null) {
                return Result.fail(ErrorCode.NOT_LOGIN_ERROR);
            }
            
            boolean success = postsService.publishPost(id, authorId);
            return Result.success(success);
        } catch (Exception e) {
            log.error("发布文章失败", e);
            return Result.fail(ErrorCode.OPERATION_ERROR, "发布文章失败: " + e.getMessage());
        }
    }

    /**
     * 取消发布文章
     * 将已发布的文章转为草稿状态
     * 
     * @param id 文章ID
     * @param request HTTP请求对象
     * @return 操作结果
     */
    @PutMapping("/{id}/unpublish")
    public Result<Boolean> unpublishPost(@PathVariable Long id, HttpServletRequest request) {
        try {
            // 获取当前用户ID
            Long authorId = getCurrentUserId(request);
            if (authorId == null) {
                return Result.fail(ErrorCode.NOT_LOGIN_ERROR);
            }
            
            boolean success = postsService.unpublishPost(id, authorId);
            return Result.success(success);
        } catch (Exception e) {
            log.error("取消发布文章失败", e);
            return Result.fail(ErrorCode.OPERATION_ERROR, "取消发布文章失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前登录用户ID
     * 这里需要根据实际的认证机制实现
     * 
     * @param request HTTP请求对象
     * @return 用户ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        // TODO: 根据实际的认证机制获取用户ID
        // 例如：从JWT token中解析用户ID
        // 或者从Session中获取用户ID
        // 这里暂时返回null，需要根据项目的认证方式实现
        
        // 示例：从请求头中获取用户ID（仅供参考）
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null) {
            try {
                return Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                log.warn("无效的用户ID格式: {}", userIdHeader);
            }
        }
        
        return null;
    }
}