package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.PostCreateReq;
import chat.liuxin.liutech.req.PostUpdateReq;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PostListResp;
import chat.liuxin.liutech.resp.PostCreateResp;
import chat.liuxin.liutech.resp.PostDetailResp;
import chat.liuxin.liutech.service.PostsAdminService;
import chat.liuxin.liutech.service.PostsService;
import chat.liuxin.liutech.utils.UserUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 管理端文章控制器
 * 需要管理员权限才能访问（类级 @PreAuthorize 保证认证，方法内不再重复判空；
 * 异常由 GlobalExceptionHandler 统一兜底，方法内不再 try-catch）
 */
@RestController
@RequestMapping("/admin/posts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PostsAdminController extends BaseAdminController {

    private final PostsService postsService;
    private final PostsAdminService postsAdminService;
    private final UserUtils userUtils;

    /** 分页查询文章列表 */
    @GetMapping
    public Result<PageResp<PostListResp>> getPostList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long seriesId,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        return Result.success(postsAdminService.getPostListForAdmin(page, size, title, categoryId, status, authorId, seriesId, includeDeleted));
    }

    /** 根据ID查询文章详情 */
    @GetMapping("/{id}")
    public Result<PostDetailResp> getPostById(@PathVariable Long id) {
        return checkResourceExists(postsAdminService.getPostDetailForAdmin(id), ErrorCode.ARTICLE_NOT_FOUND);
    }

    /** 创建文章 */
    @PostMapping
    @OperationLog(action = "create", targetType = "post", description = "创建文章")
    public Result<PostCreateResp> createPost(@Valid @RequestBody PostCreateReq req) {
        return Result.success("文章创建成功", postsService.createPost(req, userUtils.getCurrentUserId()));
    }

    /** 更新文章 */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "post", description = "更新文章")
    public Result<String> updatePost(@PathVariable Long id, @Valid @RequestBody PostUpdateReq req) {
        req.setId(id);
        boolean success = postsService.updatePostForAdmin(req, userUtils.getCurrentUserId());
        return handleOperationResult(success, "文章更新成功", "文章更新");
    }

    /** 删除文章 */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "post", description = "删除文章")
    public Result<String> deletePost(@PathVariable Long id) {
        boolean success = postsAdminService.deletePostForAdmin(id, userUtils.getCurrentUserId());
        return handleOperationResult(success, "文章删除成功", "文章删除");
    }

    /** 批量删除文章 */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "post", description = "批量删除文章")
    public Result<String> batchDeletePosts(@RequestBody List<Long> ids) {
        return handleOperationResult(postsAdminService.removeByIds(ids), "批量删除文章成功", "批量删除文章");
    }

    /** 更新文章状态 */
    @PutMapping("/{id}/status")
    @OperationLog(action = "update", targetType = "post", description = "更新文章状态")
    public Result<String> updatePostStatus(@PathVariable Long id, @RequestParam String status) {
        boolean success = postsAdminService.updatePostStatusForAdmin(id, status, userUtils.getCurrentUserId());
        return handleOperationResult(success, "文章状态更新成功", "文章状态更新");
    }

    /** 批量更新文章状态 */
    @PutMapping("/batch/status")
    @OperationLog(action = "update", targetType = "post", description = "批量更新文章状态")
    public Result<String> batchUpdatePostStatus(@RequestBody List<Long> ids, @RequestParam String status) {
        return handleOperationResult(postsAdminService.batchUpdateStatus(ids, status), "批量更新文章状态成功", "批量更新文章状态");
    }

    /** 发布文章 */
    @PutMapping("/{id}/publish")
    @OperationLog(action = "publish", targetType = "post", description = "发布文章")
    public Result<String> publishPost(@PathVariable Long id) {
        boolean success = postsAdminService.updatePostStatusForAdmin(id, "published", userUtils.getCurrentUserId());
        return handleOperationResult(success, "文章发布成功", "文章发布");
    }

    /** 下线文章 */
    @PutMapping("/{id}/offline")
    @OperationLog(action = "offline", targetType = "post", description = "下线文章")
    public Result<String> offlinePost(@PathVariable Long id) {
        boolean success = postsAdminService.updatePostStatusForAdmin(id, "draft", userUtils.getCurrentUserId());
        return handleOperationResult(success, "文章下线成功", "文章下线");
    }

    /** 恢复已删除的文章 */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "post", description = "恢复文章")
    public Result<String> restorePost(@PathVariable Long id) {
        return handleOperationResult(postsAdminService.restorePost(id), "文章恢复成功", "文章恢复");
    }

    /** 批量恢复已删除的文章 */
    @PutMapping("/batch/restore")
    @OperationLog(action = "restore", targetType = "post", description = "批量恢复文章")
    public Result<String> batchRestorePosts(@RequestBody List<Long> ids) {
        return handleOperationResult(postsAdminService.batchRestorePosts(ids), "批量恢复文章成功", "批量恢复文章");
    }

    /** 彻底删除文章（物理删除） */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "post", description = "彻底删除文章")
    public Result<String> permanentDeletePost(@PathVariable Long id) {
        return handleOperationResult(postsAdminService.permanentDeletePost(id), "文章彻底删除成功", "文章彻底删除");
    }

    /** 批量彻底删除文章（物理删除） */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "post", description = "批量彻底删除文章")
    public Result<String> batchPermanentDeletePosts(@RequestBody List<Long> ids) {
        return handleOperationResult(postsAdminService.batchPermanentDeletePosts(ids), "批量彻底删除文章成功", "批量彻底删除");
    }
}
