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
 * 管理端评论控制器（类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）
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

    /** 分页查询评论列表（支持按文章/用户/状态过滤，可选包含已删除） */
    @GetMapping
    public Result<PageResp<Comments>> getCommentList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        return Result.success(commentsAdminService.getCommentListForAdmin(page, size, postId, userId, status, includeDeleted));
    }

    /** 根据ID查询评论详情 */
    @GetMapping("/{id}")
    public Result<Comments> getCommentById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "评论ID");
        return checkResourceExists(commentsAdminService.getCommentById(id), ErrorCode.COMMENT_NOT_FOUND);
    }

    /** 软删除评论 */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "comment", description = "软删除评论", targetName = "#id")
    public Result<String> deleteComment(@PathVariable Long id) {
        ValidationUtil.validateId(id, "评论ID");
        return handleOperationResult(commentsAdminService.softDeleteComment(id), "评论删除成功", "评论删除");
    }

    /** 批量软删除评论 */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "comment", description = "批量软删除评论")
    public Result<String> batchDeleteComments(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "评论ID列表");
        return handleOperationResult(commentsAdminService.batchSoftDeleteComments(ids), "批量删除评论成功", "批量删除评论");
    }

    /** 恢复已删除的评论 */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "comment", description = "恢复评论", targetName = "#id")
    public Result<String> restoreComment(@PathVariable Long id) {
        ValidationUtil.validateId(id, "评论ID");
        return handleOperationResult(commentsAdminService.restoreComment(id), "评论恢复成功", "评论恢复");
    }

    /** 彻底删除评论（物理删除） */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "comment", description = "彻底删除评论", targetName = "#id")
    public Result<String> permanentDeleteComment(@PathVariable Long id) {
        ValidationUtil.validateId(id, "评论ID");
        return handleOperationResult(commentsAdminService.permanentDeleteComment(id), "评论彻底删除成功", "评论彻底删除");
    }

    /** 批量彻底删除评论（物理删除） */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "comment", description = "批量彻底删除评论")
    public Result<String> batchPermanentDeleteComments(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "评论ID列表");
        return handleOperationResult(commentsAdminService.batchPermanentDeleteComments(ids), "批量彻底删除评论成功", "批量彻底删除评论");
    }
}
