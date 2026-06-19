package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.resp.CheckinResp;
import chat.liuxin.liutech.resp.CheckinStatusResp;
import chat.liuxin.liutech.service.CheckinService;
import chat.liuxin.liutech.utils.UserUtils;
import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 签到控制器
 *
 * @author 刘鑫
 * @since 2025-01-30
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;
    private final UserUtils userUtils;

    /**
     * 每日签到
     *
     * @return 签到结果
     */
    @PostMapping("/checkin")
    @OperationLog(action = "checkin", targetType = "user", description = "用户签到")
    public Result<CheckinResp> checkin() {
        try {
            Long userId = userUtils.getCurrentUserId();
            if (userId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED);
            }
            return checkinService.checkin(userId);
        } catch (Exception e) {
            log.error("签到接口异常", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取签到状态
     *
     * @return 签到状态
     */
    @GetMapping("/checkin/status")
    public Result<CheckinStatusResp> getCheckinStatus() {
        try {
            Long userId = userUtils.getCurrentUserId();
            if (userId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED);
            }
            return checkinService.getCheckinStatus(userId);
        } catch (Exception e) {
            log.error("获取签到状态接口异常", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR);
        }
    }
}
