package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.CommentsAdminService;
import chat.liuxin.liutech.utils.ValidationUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端评论控制器
 * 需要管理员权限才能访问
 *
 * @author 刘鑫
 */
@Slf4j
@RestController
@RequestMapping("/admin/comments")
@PreAuthorize("hasRole('ADMIN')")
public class CommentsAdminController extends BaseAdminController {

    @Autowired
    private CommentsAdminService commentsAdminService;

    /**
     * 分页查询评论列表
     * 支持按文章ID、用户ID、状态过滤，可选包含已删除评论
     */
    @GetMapping
    public Result<PageResp<Comments>> getCommentList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        try {
            PageResp<Comments> result = commentsAdminService.getCommentListForAdmin(page, size, postId, userId, status, includeDeleted);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询评论列表");
        }
    }

    /**
     * 根据ID查询评论详情
     */
    @GetMapping("/{id}")
    public Result<Comments> getCommentById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "评论ID");
        try {
            Comments comment = commentsAdminService.getCommentById(id);
            return checkResourceExists(comment, ErrorCode.COMMENT_NOT_FOUND);
        } catch (Exception e) {
            return handleException(e, "查询评论详情");
        }
    }

    /**
     * 软删除评论
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "comment", description = "软删除评论", targetName = "#id")
    public Result<String> deleteComment(@PathVariable Long id) {
        ValidationUtil.validateId(id, "评论ID");
        try {
            boolean success = commentsAdminService.softDeleteComment(id);
            return handleOperationResult(success, "评论删除成功", "评论删除");
        } catch (Exception e) {
            return handleException(e, "评论删除");
        }
    }

    /**
     * 批量软删除评论
     */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "comment", description = "批量软删除评论")
    public Result<String> batchDeleteComments(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "评论ID列表");
        try {
            boolean success = commentsAdminService.batchSoftDeleteComments(ids);
            return handleOperationResult(success, "批量删除评论成功", "批量删除评论");
        } catch (Exception e) {
            return handleException(e, "批量删除评论");
        }
    }

    /**
     * 恢复已删除的评论
     */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "comment", description = "恢复评论", targetName = "#id")
    public Result<String> restoreComment(@PathVariable Long id) {
        ValidationUtil.validateId(id, "评论ID");
        try {
            boolean success = commentsAdminService.restoreComment(id);
            return handleOperationResult(success, "评论恢复成功", "评论恢复");
        } catch (Exception e) {
            return handleException(e, "评论恢复");
        }
    }

    /**
     * 彻底删除评论（物理删除）
     */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "comment", description = "彻底删除评论", targetName = "#id")
    public Result<String> permanentDeleteComment(@PathVariable Long id) {
        ValidationUtil.validateId(id, "评论ID");
        try {
            boolean success = commentsAdminService.permanentDeleteComment(id);
            return handleOperationResult(success, "评论彻底删除成功", "评论彻底删除");
        } catch (Exception e) {
            return handleException(e, "评论彻底删除");
        }
    }

    /**
     * 批量彻底删除评论（物理删除）
     */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "comment", description = "批量彻底删除评论")
    public Result<String> batchPermanentDeleteComments(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "评论ID列表");
        try {
            boolean success = commentsAdminService.batchPermanentDeleteComments(ids);
            return handleOperationResult(success, "批量彻底删除评论成功", "批量彻底删除评论");
        } catch (Exception e) {
            return handleException(e, "批量彻底删除评论");
        }
    }
}
