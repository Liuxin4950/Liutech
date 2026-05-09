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
import chat.liuxin.liutech.service.PostsService;
import chat.liuxin.liutech.utils.UserUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 管理端文章控制器
 * 需要管理员权限才能访问
 */
@RestController
@RequestMapping("/admin/posts")
@PreAuthorize("hasRole('ADMIN')")
public class PostsAdminController extends BaseAdminController {

    @Autowired
    private PostsService postsService;

    @Autowired
    private UserUtils userUtils;

    /**
     * 分页查询文章列表
     */
    @GetMapping
    public Result<PageResp<PostListResp>> getPostList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        try {
            PageResp<PostListResp> result = postsService.getPostListForAdmin(page, size, title, categoryId, status, authorId, includeDeleted);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询文章列表");
        }
    }

    /**
     * 根据ID查询文章详情
     */
    @GetMapping("/{id}")
    public Result<PostDetailResp> getPostById(@PathVariable Long id) {
        try {
            PostDetailResp post = postsService.getPostDetailForAdmin(id);
            return checkResourceExists(post, ErrorCode.ARTICLE_NOT_FOUND);
        } catch (Exception e) {
            return handleException(e, "查询文章详情");
        }
    }

    /**
     * 创建文章
     */
    @PostMapping
    @OperationLog(action = "create", targetType = "post", description = "创建文章: #req.title", targetName = "#req.title")
    public Result<PostCreateResp> createPost(@Valid @RequestBody PostCreateReq req) {
        try {
            // 获取当前管理员用户ID
            Long currentUserId = userUtils.getCurrentUserId();
            if (currentUserId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
            }

            PostCreateResp result = postsService.createPost(req, currentUserId);
            return Result.success("文章创建成功", result);
        } catch (Exception e) {
            return handleException(e, "文章创建");
        }
    }

    /**
     * 更新文章
     */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "post", description = "更新文章", targetName = "#req.title")
    public Result<String> updatePost(@PathVariable Long id, @Valid @RequestBody PostUpdateReq req) {
        try {
            // 获取当前管理员用户ID
            Long currentUserId = userUtils.getCurrentUserId();
            if (currentUserId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
            }

            req.setId(id);
            boolean success = postsService.updatePost(req, currentUserId);
            return handleOperationResult(success, "文章更新成功", "文章更新");
        } catch (Exception e) {
            return handleException(e, "文章更新");
        }
    }

    /**
     * 删除文章
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "post", description = "删除文章", targetName = "#id")
    public Result<String> deletePost(@PathVariable Long id) {
        try {
            Long operatorId = userUtils.getCurrentUserId();
            if (operatorId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
            }
            boolean success = postsService.deletePostForAdmin(id, operatorId);
            return handleOperationResult(success, "文章删除成功", "文章删除");
        } catch (Exception e) {
            return handleException(e, "文章删除");
        }
    }

    /**
     * 批量删除文章
     */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "post", description = "批量删除文章")
    public Result<String> batchDeletePosts(@RequestBody List<Long> ids) {
        try {
            boolean success = postsService.removeByIds(ids);
            return handleOperationResult(success, "批量删除文章成功", "批量删除文章");
        } catch (Exception e) {
            return handleException(e, "批量删除文章");
        }
    }

    /**
     * 更新文章状态
     */
    @PutMapping("/{id}/status")
    @OperationLog(action = "update", targetType = "post", description = "更新文章状态: #status", targetName = "#id")
    public Result<String> updatePostStatus(@PathVariable Long id, @RequestParam String status) {
        try {
            Long currentUserId = userUtils.getCurrentUserId();
            if (currentUserId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
            }
            boolean success = postsService.updatePostStatusForAdmin(id, status, currentUserId);
            return handleOperationResult(success, "文章状态更新成功", "文章状态更新");
        } catch (Exception e) {
            return handleException(e, "文章状态更新");
        }
    }

    /**
     * 批量更新文章状态
     */
    @PutMapping("/batch/status")
    @OperationLog(action = "update", targetType = "post", description = "批量更新文章状态: #status")
    public Result<String> batchUpdatePostStatus(@RequestBody List<Long> ids, @RequestParam String status) {
        try {
            boolean success = postsService.batchUpdateStatus(ids, status);
            return handleOperationResult(success, "批量更新文章状态成功", "批量更新文章状态");
        } catch (Exception e) {
            return handleException(e, "批量更新文章状态");
        }
    }

    /**
     * 发布文章
     */
    @PutMapping("/{id}/publish")
    @OperationLog(action = "publish", targetType = "post", description = "发布文章", targetName = "#id")
    public Result<String> publishPost(@PathVariable Long id) {
        try {
            Long currentUserId = userUtils.getCurrentUserId();
            if (currentUserId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
            }
            boolean success = postsService.updatePostStatusForAdmin(id, "published", currentUserId);
            return handleOperationResult(success, "文章发布成功", "文章发布");
        } catch (Exception e) {
            return handleException(e, "文章发布");
        }
    }

    /**
     * 下线文章
     */
    @PutMapping("/{id}/offline")
    @OperationLog(action = "offline", targetType = "post", description = "下线文章", targetName = "#id")
    public Result<String> offlinePost(@PathVariable Long id) {
        try {
            Long currentUserId = userUtils.getCurrentUserId();
            if (currentUserId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
            }
            boolean success = postsService.updatePostStatusForAdmin(id, "draft", currentUserId);
            return handleOperationResult(success, "文章下线成功", "文章下线");
        } catch (Exception e) {
            return handleException(e, "文章下线");
        }
    }

    /**
     * 恢复已删除的文章
     */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "post", description = "恢复文章", targetName = "#id")
    public Result<String> restorePost(@PathVariable Long id) {
        try {
            boolean success = postsService.restorePost(id);
            return handleOperationResult(success, "文章恢复成功", "文章恢复");
        } catch (Exception e) {
            return handleException(e, "文章恢复");
        }
    }

    /**
     * 批量恢复已删除的文章
     */
    @PutMapping("/batch/restore")
    @OperationLog(action = "restore", targetType = "post", description = "批量恢复文章")
    public Result<String> batchRestorePosts(@RequestBody List<Long> ids) {
        try {
            boolean success = postsService.batchRestorePosts(ids);
            return handleOperationResult(success, "批量恢复文章成功", "批量恢复文章");
        } catch (Exception e) {
            return handleException(e, "批量恢复文章");
        }
    }

    /**
     * 彻底删除文章（物理删除）
     */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "post", description = "彻底删除文章", targetName = "#id")
    public Result<String> permanentDeletePost(@PathVariable Long id) {
        try {
            boolean success = postsService.permanentDeletePost(id);
            return handleOperationResult(success, "文章彻底删除成功", "文章彻底删除");
        } catch (Exception e) {
            return handleException(e, "文章彻底删除");
        }
    }

    /**
     * 批量彻底删除文章（物理删除）
     */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "post", description = "批量彻底删除文章")
    public Result<String> batchPermanentDeletePosts(@RequestBody List<Long> ids) {
        try {
            boolean success = postsService.batchPermanentDeletePosts(ids);
            return handleOperationResult(success, "批量彻底删除文章成功", "批量彻底删除文章");
        } catch (Exception e) {
            return handleException(e, "批量彻底删除文章");
        }
    }
}
