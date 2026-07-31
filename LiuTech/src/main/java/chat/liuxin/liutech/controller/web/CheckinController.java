package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.resp.CheckinCalendarItemResp;
import chat.liuxin.liutech.resp.CheckinMonthResp;
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

import java.util.List;

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
        Long userId = userUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        return Result.success(checkinService.checkin(userId));
    }

    /**
     * 获取签到状态
     *
     * @return 签到状态
     */
    @GetMapping("/checkin/status")
    public Result<CheckinStatusResp> getCheckinStatus() {
        Long userId = userUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        return Result.success(checkinService.getCheckinStatus(userId));
    }

    /**
     * 获取签到月历（某月哪些天签到过，以及当天获得的积分）
     *
     * @param year  年份，可选，默认当前年
     * @param month 月份，可选，默认当前月
     * @return 该月签到记录列表
     */
    @GetMapping("/checkin/calendar")
    public Result<List<CheckinCalendarItemResp>> getCheckinCalendar(
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {
        Long userId = userUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        return Result.success(checkinService.getCheckinCalendar(userId, year, month));
    }

    /**
     * 获取用户有过签到记录的月份列表（用于日历月份切换）
     *
     * @return 签到月份列表（按日期倒序）
     */
    @GetMapping("/checkin/months")
    public Result<List<CheckinMonthResp>> getCheckinMonths() {
        Long userId = userUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        return Result.success(checkinService.getCheckinMonths(userId));
    }
}