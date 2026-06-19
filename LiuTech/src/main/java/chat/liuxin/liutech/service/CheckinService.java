package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.UserCheckinMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.CheckinResp;
import chat.liuxin.liutech.resp.CheckinStatusResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Date;

/**
 * 签到服务类
 *
 * @author 刘鑫
 * @since 2025-01-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final UserCheckinMapper userCheckinMapper;
    private final UserMapper userMapper;
    private final PointsService pointsService;

    /**
     * 用户签到
     *
     * @param userId 用户ID
     * @return 签到结果
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckinResp checkin(Long userId) {
        LocalDate today = LocalDate.now();

        // 检查今日是否已签到
        UserCheckin todayCheckin = userCheckinMapper.findByUserIdAndDate(userId, today);
        if (todayCheckin != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "今日已签到");
        }

        // 获取用户信息
        Users user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 计算连续签到天数
        int consecutiveDays = calculateConsecutiveDays(userId, today);

        // 计算获得积分（基础1积分 + 连续签到奖励）
        BigDecimal pointsEarned = calculatePointsEarned(consecutiveDays);

        // 创建签到记录
        Date now = new Date();
        UserCheckin checkin = new UserCheckin()
                .setUserId(userId)
                .setCheckinDate(today)
                .setPointsEarned(pointsEarned)
                .setConsecutiveDays(consecutiveDays)
                .setCreatedAt(now)
                .setUpdatedAt(now);

        userCheckinMapper.insert(checkin);

        // 使用PointsService增加积分（原子操作 + 流水记录）
        pointsService.addPoints(
            userId,
            pointsEarned,
            PointsService.TYPE_CHECKIN,
            PointsService.SOURCE_SYSTEM_REWARD,
            null,
            "连续签到" + consecutiveDays + "天奖励"
        );

        // 获取用户最新积分
        Users updatedUser = userMapper.selectById(userId);
        BigDecimal newPoints = updatedUser.getPoints();

        log.info("用户{}签到成功，获得{}积分，连续签到{}天", userId, pointsEarned, consecutiveDays);

        return new CheckinResp()
                .setPointsEarned(pointsEarned)
                .setTotalPoints(newPoints)
                .setConsecutiveDays(consecutiveDays)
                .setCheckinDate(today);
    }

    /**
     * 获取签到状态
     *
     * @param userId 用户ID
     * @return 签到状态
     */
    public CheckinStatusResp getCheckinStatus(Long userId) {
        LocalDate today = LocalDate.now();

        // 检查今日是否已签到
        UserCheckin todayCheckin = userCheckinMapper.findByUserIdAndDate(userId, today);
        boolean hasCheckedInToday = todayCheckin != null;

        // 获取最后一次签到记录
        UserCheckin lastCheckin = userCheckinMapper.findLastCheckinByUserId(userId);

        // 计算连续签到天数
        int consecutiveDays = 0;
        LocalDate lastCheckinDate = null;

        if (lastCheckin != null) {
            lastCheckinDate = lastCheckin.getCheckinDate();
            if (hasCheckedInToday) {
                consecutiveDays = lastCheckin.getConsecutiveDays();
            } else {
                consecutiveDays = calculateConsecutiveDays(userId, today);
            }
        }

        // 统计总签到次数
        Integer totalCheckins = userCheckinMapper.countByUserId(userId);

        return new CheckinStatusResp()
                .setHasCheckedInToday(hasCheckedInToday)
                .setConsecutiveDays(consecutiveDays)
                .setLastCheckinDate(lastCheckinDate)
                .setTotalCheckins(totalCheckins != null ? totalCheckins : 0);
    }

    /**
     * 计算连续签到天数
     */
    private int calculateConsecutiveDays(Long userId, LocalDate currentDate) {
        List<UserCheckin> recentCheckins = userCheckinMapper.findRecentCheckins(userId, 100);

        if (recentCheckins.isEmpty()) {
            return 1; // 首次签到
        }

        int consecutiveDays = 1;
        LocalDate expectedDate = currentDate.minusDays(1);

        for (UserCheckin checkin : recentCheckins) {
            if (checkin.getCheckinDate().equals(expectedDate)) {
                consecutiveDays++;
                expectedDate = expectedDate.minusDays(1);
            } else {
                break; // 断签了
            }
        }

        return consecutiveDays;
    }

    /**
     * 计算获得积分
     */
    private BigDecimal calculatePointsEarned(int consecutiveDays) {
        BigDecimal basePoints = BigDecimal.ONE;
        BigDecimal bonusPoints = BigDecimal.ZERO;

        if (consecutiveDays >= 30) {
            bonusPoints = new BigDecimal("5");
        } else if (consecutiveDays >= 7) {
            bonusPoints = BigDecimal.ONE;
        }

        return basePoints.add(bonusPoints);
    }
}
