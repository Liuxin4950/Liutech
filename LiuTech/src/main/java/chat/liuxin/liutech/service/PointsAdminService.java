package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.PointsTransactionMapper;
import chat.liuxin.liutech.mapper.UserCheckinMapper;
import chat.liuxin.liutech.common.PageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import chat.liuxin.liutech.model.PointsTransaction;
import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PointsTransactionResp;
import chat.liuxin.liutech.resp.UserCheckinResp;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分与签到管理服务（管理端）
 * 提供积分流水查询、手动调整积分、签到记录查询、积分统计等管理功能
 *
 * @author 刘鑫
 * @date 2025-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsAdminService {

    private final PointsTransactionMapper pointsTransactionMapper;

    private final UserCheckinMapper userCheckinMapper;

    private final PointsService pointsService;

    /**
     * 分页查询积分流水（关联用户名）
     *
     * @param page            页码（从1开始）
     * @param size            每页大小
     * @param userId          用户ID（可选）
     * @param transactionType 交易类型（可选）
     * @param startTime       开始时间（可选）
     * @param endTime         结束时间（可选）
     * @return 分页积分流水列表（含用户名）
     */
    @Transactional(readOnly = true)
    public PageResp<PointsTransactionResp> getTransactionList(int page, int size, Long userId,
                                                              String transactionType, Date startTime, Date endTime) {
        log.debug("查询积分流水 - 页码: {}, 每页: {}, 用户ID: {}, 交易类型: {}, 时间范围: {} ~ {}",
                page, size, userId, transactionType, startTime, endTime);

        PageQuery query = PageQuery.of(page, size);
        Long total = pointsTransactionMapper.countTransactionsForAdmin(userId, transactionType, startTime, endTime);

        List<PointsTransactionResp> records = pointsTransactionMapper.selectTransactionsForAdmin(
                query.offset(), (int) query.size(), userId, transactionType, startTime, endTime);

        return new PageResp<>(records, total, query.current(), query.size());
    }

    /**
     * 查询某用户的积分流水
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页积分流水列表
     */
    @Transactional(readOnly = true)
    public PageResp<PointsTransaction> getTransactionsByUserId(Long userId, int page, int size) {
        log.debug("查询用户积分流水 - 用户ID: {}, 页码: {}, 每页: {}", userId, page, size);

        LambdaQueryWrapper<PointsTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsTransaction::getUserId, userId)
               .orderByDesc(PointsTransaction::getCreatedAt, PointsTransaction::getId);

        Page<PointsTransaction> result = pointsTransactionMapper.selectPage(PageQuery.of(page, size).toPage(), wrapper);
        return new PageResp<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 管理员手动调整积分
     * 复用 PointsService 的用户行锁、条件更新和流水事务
     *
     * @param userId      目标用户ID
     * @param amount      调整金额（正数增加，负数减少）
     * @param description 调整原因
     * @throws BusinessException 当用户不存在、积分不足或乐观锁冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustPoints(Long userId, BigDecimal amount, String description) {
        log.info("管理员手动调整积分 - 用户ID: {}, 金额: {}, 原因: {}", userId, amount, description);

        // 1. 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "调整金额不能为零");
        }

        String reason = description != null ? description : "管理员手动调整积分";
        if (amount.signum() > 0) {
            pointsService.addPoints(userId, amount, PointsService.TYPE_ADMIN_ADJUST,
                    PointsService.SOURCE_ADMIN_MANUAL, null, reason);
        } else {
            pointsService.deductPoints(userId, amount.abs(), PointsService.TYPE_ADMIN_ADJUST,
                    PointsService.SOURCE_ADMIN_MANUAL, null, reason);
        }
    }

    /**
     * 分页查询签到记录（关联用户名）
     *
     * @param page      页码（从1开始）
     * @param size      每页大小
     * @param userId    用户ID（可选）
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 分页签到记录列表（含用户名）
     */
    @Transactional(readOnly = true)
    public PageResp<UserCheckinResp> getCheckinList(int page, int size, Long userId,
                                                    LocalDate startDate, LocalDate endDate) {
        log.debug("查询签到记录 - 页码: {}, 每页: {}, 用户ID: {}, 日期范围: {} ~ {}",
                page, size, userId, startDate, endDate);

        PageQuery query = PageQuery.of(page, size);
        Long total = userCheckinMapper.countCheckinsForAdmin(userId, startDate, endDate);

        List<UserCheckinResp> records = userCheckinMapper.selectCheckinsForAdmin(
                query.offset(), (int) query.size(), userId, startDate, endDate);

        return new PageResp<>(records, total, query.current(), query.size());
    }

    /**
     * 查询某用户的签到记录
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页签到记录列表
     */
    @Transactional(readOnly = true)
    public PageResp<UserCheckin> getCheckinsByUserId(Long userId, int page, int size) {
        log.debug("查询用户签到记录 - 用户ID: {}, 页码: {}, 每页: {}", userId, page, size);

        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
               .orderByDesc(UserCheckin::getCheckinDate, UserCheckin::getId);

        Page<UserCheckin> result = userCheckinMapper.selectPage(PageQuery.of(page, size).toPage(), wrapper);
        return new PageResp<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 获取积分统计信息
     * 包括：总发放积分、总消耗积分、总用户积分余额
     * 使用 SQL 聚合查询，避免全量加载数据到内存
     *
     * @return 统计数据 Map
     */
    public Map<String, BigDecimal> getPointsStats() {
        log.debug("查询积分统计信息");

        Map<String, BigDecimal> stats = new HashMap<>();

        // 总发放积分（签到 + 管理员增加 + 退款）- SQL 聚合
        BigDecimal totalIssued = pointsTransactionMapper.sumIssuedPoints();
        stats.put("totalIssued", totalIssued != null ? totalIssued : BigDecimal.ZERO);

        // 总消耗积分 - SQL 聚合（取绝对值）
        BigDecimal totalConsumed = pointsTransactionMapper.sumConsumedPoints();
        stats.put("totalConsumed", totalConsumed != null ? totalConsumed.abs() : BigDecimal.ZERO);

        // 总用户积分余额 - SQL 聚合
        BigDecimal totalBalance = pointsTransactionMapper.sumTotalUserPoints();
        stats.put("totalBalance", totalBalance != null ? totalBalance : BigDecimal.ZERO);

        return stats;
    }

}
