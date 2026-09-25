package chat.liuxin.liutech.controller.web;

import lombok.RequiredArgsConstructor;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.CreateMessageReq;
import chat.liuxin.liutech.resp.MessageResp;
import chat.liuxin.liutech.service.MessagesService;
import lombok.extern.slf4j.Slf4j;

/**
 * 留言控制器（公开访问，无需登录）
 */
@Slf4j
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessagesController {

    private final MessagesService messagesService;

    /**
     * 获取已审核的公开留言列表
     */
    @GetMapping("/public")
    public Result<List<MessageResp>> getPublicMessages() {
        List<MessageResp> messages = messagesService.getApprovedMessages();
        return Result.success("查询成功", messages);
    }

    /**
     * 提交留言（无需登录）
     */
    @PostMapping
    @OperationLog(action = "create", targetType = "message", description = "提交留言")
    public Result<MessageResp> createMessage(@Valid @RequestBody CreateMessageReq req) {
        // 业务失败由服务层抛 BusinessException（含友好消息），此处仅记录日志并上抛，
        // 由 GlobalExceptionHandler 统一转换响应，避免把原始异常消息暴露给客户端
        try {
            MessageResp message = messagesService.createMessage(req);
            return Result.success("留言提交成功，等待管理员审核", message);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("留言提交失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "留言提交失败，请稍后重试");
        }
    }
}
