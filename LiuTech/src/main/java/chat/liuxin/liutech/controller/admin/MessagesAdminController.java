package chat.liuxin.liutech.controller.admin;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Messages;
import chat.liuxin.liutech.service.MessagesService;
import chat.liuxin.liutech.utils.UserUtils;
import chat.liuxin.liutech.utils.ValidationUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理端留言控制器
 * 需要管理员权限才能访问
 */
@Slf4j
@RestController
@RequestMapping("/admin/messages")
@PreAuthorize("hasRole('ADMIN')")
public class MessagesAdminController extends BaseAdminController {

    @Autowired
    private MessagesService messagesService;

    @Autowired
    private UserUtils userUtils;

    /**
     * 分页查询留言列表
     */
    @GetMapping
    public Result<IPage<Messages>> getMessagesList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        try {
            IPage<Messages> result = messagesService.getMessagesForAdmin(page, size, nickname, status, includeDeleted);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询留言列表");
        }
    }

    /**
     * 根据ID查询留言详情
     */
    @GetMapping("/{id}")
    public Result<Messages> getMessageById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "留言ID");
        try {
            Messages message = messagesService.getById(id);
            return checkResourceExists(message, ErrorCode.NOT_FOUND);
        } catch (Exception e) {
            return handleException(e, "查询留言详情");
        }
    }

    /**
     * 审核留言（通过/拒绝）
     */
    @PutMapping("/{id}/review")
    @OperationLog(action = "review", targetType = "message", description = "审核留言")
    public Result<String> reviewMessage(
            @PathVariable Long id,
            @RequestBody ReviewRequest reviewRequest) {
        ValidationUtil.validateId(id, "留言ID");
        ValidationUtil.validateNotNull(reviewRequest, "审核信息");
        try {
            Long adminId = userUtils.getCurrentUserId();
            boolean success = messagesService.reviewMessage(id, reviewRequest.getStatus(), adminId);
            return handleOperationResult(success, "审核成功", "留言审核");
        } catch (Exception e) {
            return handleException(e, "留言审核");
        }
    }

    /**
     * 回复留言
     */
    @PutMapping("/{id}/reply")
    @OperationLog(action = "reply", targetType = "message", description = "回复留言")
    public Result<String> replyMessage(
            @PathVariable Long id,
            @RequestBody ReplyRequest replyRequest) {
        ValidationUtil.validateId(id, "留言ID");
        ValidationUtil.validateNotNull(replyRequest, "回复信息");
        try {
            Long adminId = userUtils.getCurrentUserId();
            boolean success = messagesService.replyMessage(id, replyRequest.getReply(), adminId);
            return handleOperationResult(success, "回复成功", "留言回复");
        } catch (Exception e) {
            return handleException(e, "留言回复");
        }
    }

    /**
     * 删除留言（软删除）
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "message", description = "删除留言")
    public Result<String> deleteMessage(@PathVariable Long id) {
        ValidationUtil.validateId(id, "留言ID");
        try {
            boolean success = messagesService.deleteMessage(id);
            return handleOperationResult(success, "删除成功", "留言删除");
        } catch (Exception e) {
            return handleException(e, "留言删除");
        }
    }

    /**
     * 批量删除留言（软删除）
     */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "message", description = "批量删除留言")
    public Result<String> batchDeleteMessages(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "留言ID列表");
        try {
            boolean success = messagesService.batchDeleteMessages(ids);
            return handleOperationResult(success, "批量删除成功", "批量删除留言");
        } catch (Exception e) {
            return handleException(e, "批量删除留言");
        }
    }

    /**
     * 恢复已删除的留言
     */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "message", description = "恢复留言")
    public Result<String> restoreMessage(@PathVariable Long id) {
        ValidationUtil.validateId(id, "留言ID");
        try {
            boolean success = messagesService.restoreMessage(id);
            return handleOperationResult(success, "恢复成功", "留言恢复");
        } catch (Exception e) {
            return handleException(e, "留言恢复");
        }
    }

    /**
     * 彻底删除留言（物理删除）
     */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "message", description = "彻底删除留言")
    public Result<String> permanentDeleteMessage(@PathVariable Long id) {
        ValidationUtil.validateId(id, "留言ID");
        try {
            boolean success = messagesService.permanentDeleteMessage(id);
            return handleOperationResult(success, "彻底删除成功", "留言彻底删除");
        } catch (Exception e) {
            return handleException(e, "留言彻底删除");
        }
    }

    /**
     * 批量彻底删除留言（物理删除）
     */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "message", description = "批量彻底删除留言")
    public Result<String> batchPermanentDeleteMessages(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "留言ID列表");
        try {
            boolean success = messagesService.batchPermanentDeleteMessages(ids);
            return handleOperationResult(success, "批量彻底删除成功", "批量彻底删除留言");
        } catch (Exception e) {
            return handleException(e, "批量彻底删除留言");
        }
    }

    /**
     * 审核请求
     */
    public static class ReviewRequest {
        /**
         * 状态：1-通过，2-拒绝
         */
        private Integer status;

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    /**
     * 回复请求
     */
    public static class ReplyRequest {
        /**
         * 回复内容
         */
        private String reply;

        public String getReply() {
            return reply;
        }

        public void setReply(String reply) {
            this.reply = reply;
        }
    }
}
