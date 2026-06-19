package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.CreateMessageReq;
import chat.liuxin.liutech.resp.MessageResp;
import chat.liuxin.liutech.service.MessagesService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * 留言控制器（公开访问，无需登录）
 */
@Slf4j
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
 {

    private final MessagesService messagesService;

    /**
     * 获取已审核的公开留言列表
     */
    @GetMapping("/public")
    public Result<List<MessageResp>> getPublicMessages() {
        log.info("查询公开留言列表");
        List<MessageResp> messages = messagesService.getApprovedMessages();
        return Result.success("查询成功", messages);
    }

    /**
     * 提交留言（无需登录）
     */
    @PostMapping
    @OperationLog(action = "create", targetType = "message", description = "提交留言")
    public Result<MessageResp> createMessage(@Valid @RequestBody CreateMessageReq req) {
        log.info("收到留言提交请求");
        try {
            MessageResp message = messagesService.createMessage(req);
            return Result.success("留言提交成功，等待管理员审核", message);
        } catch (Exception e) {
            log.error("留言提交失败", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR, "留言提交失败: " + e.getMessage());
        }
    }
}
