package chat.liuxin.liutech.service;

import java.math.BigDecimal;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import chat.liuxin.liutech.mapper.PointsTransactionMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.PointsTransaction;
import chat.liuxin.liutech.model.Users;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * 积分服务类 - 统一管理积分变动
 *
 * 核心安全特性：
 * 1. 使用乐观锁防止并发竞态条件
 * 2. 记录完整的积分流水
 * 3. 原子性的积分扣减操作
 *
 * @author 刘鑫
 * @date 2025-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
 {

    private final UserMapper userMapper;

    private final PointsTransactionMapper pointsTransactionMapper;

    /**
     * 交易类型常量
     */
    public static final String TYPE_CHECKIN = "checkin";            // 签到
    public static final String TYPE_CONSUMPTION = "consumption";    // 消费
    public static final String TYPE_REFUND = "refund";              // 退款
    public static final String TYPE_ADMIN_ADJUST = "admin_adjust";  // 管理员调整

    /**
     * 来源类型常量
     */
    public static final String SOURCE_RESOURCE_DOWNLOAD = "resource_download";
    public static final String SOURCE_ADMIN_MANUAL = "admin_manual";
    public static final String SOURCE_SYSTEM_REWARD = "system_reward";

    /**
     * 扣减用户积分（原子操作，防止并发问题）
     *
     * 实现原理：
     * 1. 使用乐观锁（version字段）防止并发修改
     * 2. 使用SQL的 WHERE 条件确保积分充足
     * 3. 记录积分流水
     *
     * @param userId 用户ID
     * @param amount 扣减金额（必须为正数）
     * @param sourceType 来源类型
     * @param sourceId 来源ID
     * @param description 描述
     * @throws RuntimeException 积分不足或用户不存在时抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void deductPoints(Long userId, BigDecimal amount, String sourceType, Long sourceId, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("扣减金额必须大于0");
        }

        // 1. 查询用户当前积分和版本号
        Users user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        BigDecimal currentPoints = user.getPoints() != null ? user.getPoints() : BigDecimal.ZERO;
        Integer currentVersion = user.getVersion() != null ? user.getVersion() : 0;

        // 2. 检查积分是否充足
        if (currentPoints.compareTo(amount) < 0) {
            throw new RuntimeException("积分不足，需要 " + amount + " 积分，当前仅有 " + currentPoints + " 积分");
        }

        // 3. 原子性扣减积分（使用乐观锁 + WHERE条件双重保证）
        BigDecimal newPoints = currentPoints.subtract(amount);
        Integer newVersion = currentVersion + 1;

        int updateResult = userMapper.deductPointsWithVersion(userId, amount, currentVersion, newVersion);

        if (updateResult == 0) {
            // 乐观锁冲突，说明有并发修改
            log.warn("用户{}积分扣减失败，可能存在并发操作", userId);
            throw new RuntimeException("系统繁忙，请稍后重试");
        }

        // 4. 记录积分流水
        PointsTransaction transaction = new PointsTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType(TYPE_CONSUMPTION);
        transaction.setAmount(amount.negate()); // 负数表示减少
        transaction.setBalanceAfter(newPoints);
        transaction.setSourceType(sourceType);
        transaction.setSourceId(sourceId);
        transaction.setDescription(description);
        transaction.setCreatedAt(new Date());

        pointsTransactionMapper.insert(transaction);

        log.info("用户{}成功扣减{}积分，剩余{}积分，来源：{}-{}", userId, amount, newPoints, sourceType, sourceId);
    }

    /**
     * 增加用户积分（原子操作，使用乐观锁防止并发竞态）
     *
     * @param userId 用户ID
     * @param amount 增加金额（必须为正数）
     * @param transactionType 交易类型
     * @param sourceType 来源类型
     * @param sourceId 来源ID
     * @param description 描述
     */
    @Transactional(rollbackFor = Exception.class)
    public void addPoints(Long userId, BigDecimal amount, String transactionType, String sourceType, Long sourceId, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("增加金额必须大于0");
        }

        // 1. 查询用户当前积分
        Users user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 使用乐观锁更新积分（重试机制，防止并发竞态）
        boolean updated = false;
        BigDecimal newPoints = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) {
            user = userMapper.selectById(userId);
            BigDecimal currentPoints = user.getPoints() != null ? user.getPoints() : BigDecimal.ZERO;
            Integer currentVersion = user.getVersion() != null ? user.getVersion() : 0;
            int newVersion = currentVersion + 1;
            newPoints = currentPoints.add(amount);

            int rows = userMapper.addPointsWithVersion(userId, amount, currentVersion, newVersion);
            if (rows > 0) {
                updated = true;
                break;
            }
            log.warn("用户{}积分增加乐观锁冲突，重试第{}次", userId, i + 1);
        }

        if (!updated) {
            throw new RuntimeException("系统繁忙，请稍后重试");
        }

        // 3. 记录积分流水
        PointsTransaction transaction = new PointsTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType(transactionType);
        transaction.setAmount(amount); // 正数表示增加
        transaction.setBalanceAfter(newPoints);
        transaction.setSourceType(sourceType);
        transaction.setSourceId(sourceId);
        transaction.setDescription(description);
        transaction.setCreatedAt(new Date());

        pointsTransactionMapper.insert(transaction);

        log.info("用户{}成功增加{}积分，总计{}积分，来源：{}-{}", userId, amount, newPoints, sourceType, sourceId);
    }

    /**
     * 退款（恢复积分）
     *
     * @param userId 用户ID
     * @param amount 退款金额
     * @param sourceId 原始消费记录ID
     * @param description 描述
     */
    @Transactional(rollbackFor = Exception.class)
    public void refundPoints(Long userId, BigDecimal amount, Long sourceId, String description) {
        addPoints(userId, amount, TYPE_REFUND, SOURCE_RESOURCE_DOWNLOAD, sourceId, description);
    }
}
