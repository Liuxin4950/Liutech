package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.PointsTransactionMapper;
import chat.liuxin.liutech.mapper.UserCheckinMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.PointsTransaction;
import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.PointsTransactionResp;
import chat.liuxin.liutech.resp.UserCheckinResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
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
public class PointsAdminService {

    private static final int MAX_OPTIMISTIC_LOCK_RETRIES = 3;

    @Autowired
    private PointsTransactionMapper pointsTransactionMapper;

    @Autowired
    private UserCheckinMapper userCheckinMapper;

    @Autowired
    private UserMapper userMapper;

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
    public PageResp<PointsTransactionResp> getTransactionList(int page, int size, Long userId,
                                                              String transactionType, Date startTime, Date endTime) {
        log.info("查询积分流水 - 页码: {}, 每页: {}, 用户ID: {}, 交易类型: {}, 时间范围: {} ~ {}",
                page, size, userId, transactionType, startTime, endTime);

        Long total = pointsTransactionMapper.countTransactionsForAdmin(userId, transactionType, startTime, endTime);

        int offset = (page - 1) * size;
        List<PointsTransactionResp> records = pointsTransactionMapper.selectTransactionsForAdmin(
                offset, size, userId, transactionType, startTime, endTime);

        return buildTransactionPageResult(records, total, page, size);
    }

    /**
     * 查询某用户的积分流水
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页积分流水列表
     */
    public PageResp<PointsTransaction> getTransactionsByUserId(Long userId, int page, int size) {
        log.info("查询用户积分流水 - 用户ID: {}, 页码: {}, 每页: {}", userId, page, size);

        LambdaQueryWrapper<PointsTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsTransaction::getUserId, userId)
               .orderByDesc(PointsTransaction::getCreatedAt);

        Long total = pointsTransactionMapper.selectCount(wrapper);

        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + size + " OFFSET " + offset);
        List<PointsTransaction> records = pointsTransactionMapper.selectList(wrapper);

        return buildPageResult(records, total, page, size);
    }

    /**
     * 管理员手动调整积分
     * 使用乐观锁更新用户积分，并记录积分流水
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

        // 2. 查询用户
        Users user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 检查扣减时积分是否充足
        BigDecimal currentPoints = user.getPoints() != null ? user.getPoints() : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) < 0 && currentPoints.add(amount).compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户积分不足，当前积分: " + currentPoints);
        }

        // 4. 使用乐观锁更新积分（重试机制）
        boolean updated = false;
        for (int i = 0; i < MAX_OPTIMISTIC_LOCK_RETRIES; i++) {
            // 重新获取最新版本号
            user = userMapper.selectById(userId);
            int currentVersion = user.getVersion() != null ? user.getVersion() : 0;
            int newVersion = currentVersion + 1;

            int rows;
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                rows = userMapper.addPointsWithVersion(userId, amount, currentVersion, newVersion);
            } else {
                rows = userMapper.deductPointsWithVersion(userId, amount.abs(), currentVersion, newVersion);
            }

            if (rows > 0) {
                updated = true;
                break;
            }
            log.warn("乐观锁冲突，重试第 {} 次 - 用户ID: {}", i + 1, userId);
        }

        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "积分调整失败，请稍后重试");
        }

        // 5. 记录积分流水
        Users updatedUser = userMapper.selectById(userId);
        BigDecimal balanceAfter = updatedUser.getPoints() != null ? updatedUser.getPoints() : BigDecimal.ZERO;

        PointsTransaction transaction = new PointsTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType("admin_adjust");
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setSourceType("admin_manual");
        transaction.setDescription(description != null ? description : "管理员手动调整积分");
        transaction.setCreatedAt(new Date());
        pointsTransactionMapper.insert(transaction);

        log.info("积分调整成功 - 用户ID: {}, 金额: {}, 调整后余额: {}", userId, amount, balanceAfter);
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
    public PageResp<UserCheckinResp> getCheckinList(int page, int size, Long userId,
                                                    LocalDate startDate, LocalDate endDate) {
        log.info("查询签到记录 - 页码: {}, 每页: {}, 用户ID: {}, 日期范围: {} ~ {}",
                page, size, userId, startDate, endDate);

        Long total = userCheckinMapper.countCheckinsForAdmin(userId, startDate, endDate);

        int offset = (page - 1) * size;
        List<UserCheckinResp> records = userCheckinMapper.selectCheckinsForAdmin(
                offset, size, userId, startDate, endDate);

        return buildCheckinPageResult(records, total, page, size);
    }

    /**
     * 查询某用户的签到记录
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页签到记录列表
     */
    public PageResp<UserCheckin> getCheckinsByUserId(Long userId, int page, int size) {
        log.info("查询用户签到记录 - 用户ID: {}, 页码: {}, 每页: {}", userId, page, size);

        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
               .orderByDesc(UserCheckin::getCheckinDate);

        Long total = userCheckinMapper.selectCount(wrapper);

        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + size + " OFFSET " + offset);
        List<UserCheckin> records = userCheckinMapper.selectList(wrapper);

        return buildUserCheckinPageResult(records, total, page, size);
    }

    /**
     * 获取积分统计信息
     * 包括：总发放积分、总消耗积分、总用户积分余额
     * 使用 SQL 聚合查询，避免全量加载数据到内存
     *
     * @return 统计数据 Map
     */
    public Map<String, BigDecimal> getPointsStats() {
        log.info("查询积分统计信息");

        Map<String, BigDecimal> stats = new HashMap<>();

        // 总发放积分（签到 + 管理员增加 + 退款）- SQL 聚合
        BigDecimal totalIssued = pointsTransactionMapper.sumPointsByTypes(
                Arrays.asList("checkin", "admin_adjust", "refund"));
        stats.put("totalIssued", totalIssued != null ? totalIssued : BigDecimal.ZERO);

        // 总消耗积分 - SQL 聚合（取绝对值）
        BigDecimal totalConsumed = pointsTransactionMapper.sumPointsByType("consumption");
        stats.put("totalConsumed", totalConsumed != null ? totalConsumed.abs() : BigDecimal.ZERO);

        // 总用户积分余额 - SQL 聚合
        BigDecimal totalBalance = pointsTransactionMapper.sumTotalUserPoints();
        stats.put("totalBalance", totalBalance != null ? totalBalance : BigDecimal.ZERO);

        return stats;
    }

    /**
     * 构建积分流水分页结果（含用户名）
     */
    private PageResp<PointsTransactionResp> buildTransactionPageResult(List<PointsTransactionResp> records,
                                                                       Long total, int page, int size) {
        PageResp<PointsTransactionResp> pageResult = new PageResp<>();
        pageResult.setRecords(records);
        pageResult.setTotal(total);
        pageResult.setCurrent((long) page);
        pageResult.setSize((long) size);
        pageResult.setPages((long) Math.ceil((double) total / size));
        pageResult.setHasNext((long) page < pageResult.getPages());
        pageResult.setHasPrevious((long) page > 1);
        return pageResult;
    }

    /**
     * 构建积分流水分页结果（基础实体）
     */
    private PageResp<PointsTransaction> buildPageResult(List<PointsTransaction> records, Long total, int page, int size) {
        PageResp<PointsTransaction> pageResult = new PageResp<>();
        pageResult.setRecords(records);
        pageResult.setTotal(total);
        pageResult.setCurrent((long) page);
        pageResult.setSize((long) size);
        pageResult.setPages((long) Math.ceil((double) total / size));
        pageResult.setHasNext((long) page < pageResult.getPages());
        pageResult.setHasPrevious((long) page > 1);
        return pageResult;
    }

    /**
     * 构建签到记录分页结果（含用户名）
     */
    private PageResp<UserCheckinResp> buildCheckinPageResult(List<UserCheckinResp> records,
                                                             Long total, int page, int size) {
        PageResp<UserCheckinResp> pageResult = new PageResp<>();
        pageResult.setRecords(records);
        pageResult.setTotal(total);
        pageResult.setCurrent((long) page);
        pageResult.setSize((long) size);
        pageResult.setPages((long) Math.ceil((double) total / size));
        pageResult.setHasNext((long) page < pageResult.getPages());
        pageResult.setHasPrevious((long) page > 1);
        return pageResult;
    }

    /**
     * 构建签到记录分页结果（基础实体）
     */
    private PageResp<UserCheckin> buildUserCheckinPageResult(List<UserCheckin> records,
                                                             Long total, int page, int size) {
        PageResp<UserCheckin> pageResult = new PageResp<>();
        pageResult.setRecords(records);
        pageResult.setTotal(total);
        pageResult.setCurrent((long) page);
        pageResult.setSize((long) size);
        pageResult.setPages((long) Math.ceil((double) total / size));
        pageResult.setHasNext((long) page < pageResult.getPages());
        pageResult.setHasPrevious((long) page > 1);
        return pageResult;
    }
}
