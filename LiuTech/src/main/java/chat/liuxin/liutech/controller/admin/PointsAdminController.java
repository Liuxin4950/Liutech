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

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

/**
 * 管理端积分与签到控制器
 * 提供积分流水查询、手动调整积分、签到记录查询、积分统计等管理接口
 *
 * @author 刘鑫
 * @date 2025-01-18
 */
@RestController
@RequestMapping("/admin/points")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
 extends BaseAdminController {

    private final PointsAdminService pointsAdminService;

    /**
     * 分页查询积分流水
     * 支持按用户ID、交易类型、时间范围筛选
     *
     * @param page            页码（默认1）
     * @param size            每页大小（默认10）
     * @param userId          用户ID（可选）
     * @param transactionType 交易类型（可选：checkin/consumption/refund/admin_adjust）
     * @param startTime       开始时间（可选）
     * @param endTime         结束时间（可选）
     * @return 分页积分流水列表
     */
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

        try {
            PageResp<PointsTransactionResp> result = pointsAdminService.getTransactionList(
                    page, size, userId, transactionType, startTime, endTime);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询积分流水");
        }
    }

    /**
     * 查询某用户的积分流水
     *
     * @param userId 用户ID
     * @param page   页码（默认1）
     * @param size   每页大小（默认10）
     * @return 分页积分流水列表
     */
    @GetMapping("/transactions/user/{userId}")
    public Result<PageResp<PointsTransaction>> getTransactionsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        ValidationUtil.validateId(userId, "用户ID");
        ValidationUtil.validateRange(page, "页码", 1, Integer.MAX_VALUE);
        ValidationUtil.validateRange(size, "页面大小", 1, 100);

        try {
            PageResp<PointsTransaction> result = pointsAdminService.getTransactionsByUserId(userId, page, size);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询用户积分流水");
        }
    }

    /**
     * 管理员手动调整积分
     * 正数为增加积分，负数为扣减积分
     *
     * @param request 调整请求，包含 userId、amount、description
     * @return 操作结果
     */
    @PostMapping("/adjust")
    @OperationLog(action = "update", targetType = "points", description = "管理员手动调整积分")
    public Result<String> adjustPoints(@RequestBody Map<String, Object> request) {
        Long userId = request.get("userId") != null ? Long.valueOf(request.get("userId").toString()) : null;
        BigDecimal amount = request.get("amount") != null ? new BigDecimal(request.get("amount").toString()) : null;
        String description = request.get("description") != null ? request.get("description").toString() : null;

        ValidationUtil.validateId(userId, "用户ID");
        ValidationUtil.validateNotNull(amount, "调整金额");

        try {
            pointsAdminService.adjustPoints(userId, amount, description);
            return Result.success("积分调整成功");
        } catch (Exception e) {
            return handleException(e, "积分调整");
        }
    }

    /**
     * 分页查询签到记录
     * 支持按用户ID、日期范围筛选
     *
     * @param page      页码（默认1）
     * @param size      每页大小（默认10）
     * @param userId    用户ID（可选）
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 分页签到记录列表
     */
    @GetMapping("/checkins")
    public Result<PageResp<UserCheckinResp>> getCheckinList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        ValidationUtil.validateRange(page, "页码", 1, Integer.MAX_VALUE);
        ValidationUtil.validateRange(size, "页面大小", 1, 100);

        try {
            PageResp<UserCheckinResp> result = pointsAdminService.getCheckinList(page, size, userId, startDate, endDate);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询签到记录");
        }
    }

    /**
     * 查询某用户的签到记录
     *
     * @param userId 用户ID
     * @param page   页码（默认1）
     * @param size   每页大小（默认10）
     * @return 分页签到记录列表
     */
    @GetMapping("/checkins/user/{userId}")
    public Result<PageResp<UserCheckin>> getCheckinsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        ValidationUtil.validateId(userId, "用户ID");
        ValidationUtil.validateRange(page, "页码", 1, Integer.MAX_VALUE);
        ValidationUtil.validateRange(size, "页面大小", 1, 100);

        try {
            PageResp<UserCheckin> result = pointsAdminService.getCheckinsByUserId(userId, page, size);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询用户签到记录");
        }
    }

    /**
     * 获取积分统计信息
     * 包括：总发放积分、总消耗积分、总用户积分余额
     *
     * @return 统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, BigDecimal>> getPointsStats() {
        try {
            Map<String, BigDecimal> stats = pointsAdminService.getPointsStats();
            return Result.success(stats);
        } catch (Exception e) {
            return handleException(e, "查询积分统计");
        }
    }
}
