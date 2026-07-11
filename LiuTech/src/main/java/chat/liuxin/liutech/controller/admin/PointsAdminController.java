package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.PointsTransaction;
import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PointsTransactionResp;
import chat.liuxin.liutech.resp.UserCheckinResp;
import chat.liuxin.liutech.service.PointsAdminService;
import chat.liuxin.liutech.utils.ValidationUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

/**
 * 管理端积分与签到控制器（类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）
 *
 * @author 刘鑫
 * @date 2025-01-18
 */
@RestController
@RequestMapping("/admin/points")
@PreAuthorize("hasRole('ADMIN')")
public class PointsAdminController extends BaseAdminController {

    @Autowired
    private PointsAdminService pointsAdminService;

    /** 分页查询积分流水（支持按用户/类型/时间筛选） */
    @GetMapping("/transactions")
    public Result<PageResp<PointsTransactionResp>> getTransactionList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
        ValidationUtil.validateRange(page, "页码", 1, Integer.MAX_VALUE);
        ValidationUtil.validateRange(size, "页面大小", 1, 100);
        return Result.success(pointsAdminService.getTransactionList(page, size, userId, transactionType, startTime, endTime));
    }

    /** 查询某用户的积分流水 */
    @GetMapping("/transactions/user/{userId}")
    public Result<PageResp<PointsTransaction>> getTransactionsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        ValidationUtil.validateId(userId, "用户ID");
        ValidationUtil.validateRange(page, "页码", 1, Integer.MAX_VALUE);
        ValidationUtil.validateRange(size, "页面大小", 1, 100);
        return Result.success(pointsAdminService.getTransactionsByUserId(userId, page, size));
    }

    /** 管理员手动调整积分（正数增加，负数扣减） */
    @PostMapping("/adjust")
    @OperationLog(action = "update", targetType = "points", description = "管理员手动调整积分")
    public Result<String> adjustPoints(@RequestBody Map<String, Object> request) {
        Long userId = request.get("userId") != null ? Long.valueOf(request.get("userId").toString()) : null;
        BigDecimal amount = request.get("amount") != null ? new BigDecimal(request.get("amount").toString()) : null;
        String description = request.get("description") != null ? request.get("description").toString() : null;
        ValidationUtil.validateId(userId, "用户ID");
        ValidationUtil.validateNotNull(amount, "调整金额");
        pointsAdminService.adjustPoints(userId, amount, description);
        return Result.success("积分调整成功");
    }

    /** 分页查询签到记录（支持按用户/日期筛选） */
    @GetMapping("/checkins")
    public Result<PageResp<UserCheckinResp>> getCheckinList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        ValidationUtil.validateRange(page, "页码", 1, Integer.MAX_VALUE);
        ValidationUtil.validateRange(size, "页面大小", 1, 100);
        return Result.success(pointsAdminService.getCheckinList(page, size, userId, startDate, endDate));
    }

    /** 查询某用户的签到记录 */
    @GetMapping("/checkins/user/{userId}")
    public Result<PageResp<UserCheckin>> getCheckinsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        ValidationUtil.validateId(userId, "用户ID");
        ValidationUtil.validateRange(page, "页码", 1, Integer.MAX_VALUE);
        ValidationUtil.validateRange(size, "页面大小", 1, 100);
        return Result.success(pointsAdminService.getCheckinsByUserId(userId, page, size));
    }

    /** 获取积分统计信息（总发放/总消耗/总余额） */
    @GetMapping("/stats")
    public Result<Map<String, BigDecimal>> getPointsStats() {
        return Result.success(pointsAdminService.getPointsStats());
    }
}
