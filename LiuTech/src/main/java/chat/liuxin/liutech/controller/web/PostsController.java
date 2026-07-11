package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.PostCreateReq;
import chat.liuxin.liutech.req.PostQueryReq;
import chat.liuxin.liutech.req.PostUpdateReq;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostCreateResp;
import chat.liuxin.liutech.resp.PostDetailResp;
import chat.liuxin.liutech.resp.PostListResp;
import chat.liuxin.liutech.service.PostInteractionService;
import chat.liuxin.liutech.service.PostsService;
import chat.liuxin.liutech.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 文章控制器（用户前台）
 * 提供文章列表、详情、搜索、点赞收藏、草稿箱等公开/登录接口。
 * 管理员写操作（创建/更新/删除/发布）保留在此供 Web 端使用，后台管理走 /admin/posts。
 * 异常由 GlobalExceptionHandler 统一兜底，方法内不再 try-catch。
 *
 * @author 刘鑫
 */
@Slf4j
@RestController
@RequestMapping("/posts")
public class PostsController {

    @Autowired
    private PostsService postsService;

    @Autowired
    private PostInteractionService postInteractionService;

    @Autowired
    private UserUtils userUtils;

    /** 分页查询文章列表（公开，仅已发布） */
    @GetMapping
    public Result<PageResp<PostListResp>> getPostList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort) {
        Long currentUserId = userUtils.getCurrentUserId();
        PostQueryReq req = new PostQueryReq();
        req.setPage(page);
        req.setSize(size);
        req.setCategoryId(categoryId);
        req.setTagId(tagId);
        req.setKeyword(keyword);
        req.setSort(sort);
        req.setStatus("published");
        return Result.success("查询成功", postsService.getPostList(req, currentUserId));
    }

    /** 根据ID查询文章详情（公开） */
    @GetMapping("/{id}")
    public Result<PostDetailResp> getPostDetail(@PathVariable Long id) {
        Long currentUserId = userUtils.getCurrentUserId();
        PostDetailResp post = postsService.getPostDetail(id, currentUserId);
        if (post == null) {
            return Result.fail(ErrorCode.ARTICLE_NOT_FOUND);
        }
        return Result.success("查询成功", post);
    }

    /** 切换文章点赞状态（需登录） */
    @PostMapping("/{id}/like")
    public Result<String> toggleLike(@PathVariable Long id) {
        Long currentUserId = userUtils.getCurrentUserId();
        if (currentUserId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        boolean isLiked = postInteractionService.toggleLike(id, currentUserId);
        return Result.success(isLiked ? "点赞成功" : "取消点赞成功", isLiked ? "liked" : "unliked");
    }

    /** 切换文章收藏状态（需登录） */
    @PostMapping("/{id}/favorite")
    public Result<String> toggleFavorite(@PathVariable Long id) {
        Long currentUserId = userUtils.getCurrentUserId();
        if (currentUserId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        boolean isFavorited = postInteractionService.toggleFavorite(id, currentUserId);
        return Result.success(isFavorited ? "收藏成功" : "取消收藏成功", isFavorited ? "favorited" : "unfavorited");
    }

    /** 查询热门文章（公开） */
    @GetMapping("/hot")
    public Result<List<PostListResp>> getHotPosts(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success("查询成功", postsService.getHotPosts(limit));
    }

    /** 查询最新文章（公开） */
    @GetMapping("/latest")
    public Result<List<PostListResp>> getLatestPosts(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success("查询成功", postsService.getLatestPosts(limit));
    }

    /** 搜索文章（公开，仅已发布） */
    @GetMapping("/search")
    public Result<PageResp<PostListResp>> searchPosts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PostQueryReq req = new PostQueryReq();
        req.setPage(page);
        req.setSize(size);
        req.setKeyword(keyword);
        req.setStatus("published");
        return Result.success("搜索成功", postsService.getPostList(req));
    }

    /** 创建文章（管理员） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "create", targetType = "post", description = "创建文章")
    public Result<PostCreateResp> createPost(@Valid @RequestBody PostCreateReq req) {
        return Result.success(postsService.createPost(req, userUtils.getCurrentUserId()));
    }

    /** 更新文章（管理员） */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "update", targetType = "post", description = "更新文章")
    public Result<Boolean> updatePost(@PathVariable Long id, @Valid @RequestBody PostUpdateReq req) {
        req.setId(id);
        return Result.success(postsService.updatePost(req, userUtils.getCurrentUserId()));
    }

    /** 删除文章（管理员） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "delete", targetType = "post", description = "删除文章")
    public Result<Boolean> deletePost(@PathVariable Long id) {
        return Result.success(postsService.deletePost(id, userUtils.getCurrentUserId()));
    }

    /** 发布文章（管理员） */
    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "publish", targetType = "post", description = "发布文章")
    public Result<Boolean> publishPost(@PathVariable Long id) {
        return Result.success(postsService.publishPost(id, userUtils.getCurrentUserId()));
    }

    /** 取消发布文章（管理员） */
    @PutMapping("/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "offline", targetType = "post", description = "下线文章")
    public Result<Boolean> unpublishPost(@PathVariable Long id) {
        return Result.success(postsService.unpublishPost(id, userUtils.getCurrentUserId()));
    }

    /** 获取当前用户的草稿箱（需登录） */
    @GetMapping("/drafts")
    public Result<PageResp<PostListResp>> getDrafts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Long authorId = userUtils.getCurrentUserId();
        if (authorId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        PostQueryReq req = new PostQueryReq();
        req.setPage(page);
        req.setSize(size);
        req.setKeyword(keyword);
        req.setStatus("draft");
        req.setAuthorId(authorId);
        req.setSort("latest");
        return Result.success("查询成功", postsService.getPostList(req));
    }

    /** 获取当前用户的已发布文章（需登录） */
    @GetMapping("/my")
    public Result<PageResp<PostListResp>> getMyPosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Long authorId = userUtils.getCurrentUserId();
        if (authorId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        PostQueryReq req = new PostQueryReq();
        req.setPage(page);
        req.setSize(size);
        req.setKeyword(keyword);
        req.setStatus("published");
        req.setAuthorId(authorId);
        req.setSort("latest");
        return Result.success("查询成功", postsService.getPostList(req, authorId));
    }

    /** 获取当前用户的收藏文章（需登录） */
    @GetMapping("/favorites")
    public Result<PageResp<PostListResp>> getFavoritePosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Long userId = userUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        PostQueryReq req = new PostQueryReq();
        req.setPage(page);
        req.setSize(size);
        req.setKeyword(keyword);
        req.setSort("latest");
        return Result.success("查询成功", postInteractionService.getFavoritePosts(req, userId));
    }
}
