package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.UserCheckinMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.UserCheckin;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resl.CheckinResl;
import chat.liuxin.liutech.resl.CheckinStatusResl;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.ErrorCode;
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

    /**
     * 用户签到
     * 
     * @param userId 用户ID
     * @return 签到结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<CheckinResl> checkin(Long userId) {
        try {
            LocalDate today = LocalDate.now();
            
            // 检查今日是否已签到
            UserCheckin todayCheckin = userCheckinMapper.findByUserIdAndDate(userId, today);
            if (todayCheckin != null) {
                return Result.fail(ErrorCode.PARAMS_ERROR, "今日已签到");
            }

            // 获取用户信息
            Users user = userMapper.selectById(userId);
            if (user == null) {
                return Result.fail(ErrorCode.NOT_FOUND, "用户不存在");
            }

            // 计算连续签到天数
            int consecutiveDays = calculateConsecutiveDays(userId, today);
            
            // 计算获得积分（基础1积分 + 连续签到奖励）
            BigDecimal pointsEarned = calculatePointsEarned(consecutiveDays);

            // 创建签到记录（显式填充时间，兜底MyBatis-Plus自动填充）
            Date now = new Date();
            UserCheckin checkin = new UserCheckin()
                    .setUserId(userId)
                    .setCheckinDate(today)
                    .setPointsEarned(pointsEarned)
                    .setConsecutiveDays(consecutiveDays)
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
            
            userCheckinMapper.insert(checkin);

            // 更新用户积分
            BigDecimal newPoints = user.getPoints().add(pointsEarned);
            user.setPoints(newPoints);
            userMapper.updateById(user);

            // 构建响应
            CheckinResl response = new CheckinResl()
                    .setPointsEarned(pointsEarned)
                    .setTotalPoints(newPoints)
                    .setConsecutiveDays(consecutiveDays)
                    .setCheckinDate(today);

            log.info("用户{}签到成功，获得{}积分，连续签到{}天", userId, pointsEarned, consecutiveDays);
            return Result.success("签到成功", response);
            
        } catch (Exception e) {
            log.error("用户{}签到失败", userId, e);
            return Result.fail(ErrorCode.SYSTEM_ERROR, "签到失败，请稍后重试");
        }
    }

    /**
     * 获取签到状态
     * 
     * @param userId 用户ID
     * @return 签到状态
     */
    public Result<CheckinStatusResl> getCheckinStatus(Long userId) {
        try {
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
                    // 如果今天没签到，需要重新计算连续天数
                    consecutiveDays = calculateConsecutiveDays(userId, today);
                }
            }
            
            // 统计总签到次数
            Integer totalCheckins = userCheckinMapper.countByUserId(userId);
            
            CheckinStatusResl response = new CheckinStatusResl()
                    .setHasCheckedInToday(hasCheckedInToday)
                    .setConsecutiveDays(consecutiveDays)
                    .setLastCheckinDate(lastCheckinDate)
                    .setTotalCheckins(totalCheckins != null ? totalCheckins : 0);
            
            return Result.success("获取签到状态成功", response);
            
        } catch (Exception e) {
            log.error("获取用户{}签到状态失败", userId, e);
            return Result.fail(ErrorCode.SYSTEM_ERROR, "获取签到状态失败");
        }
    }

    /**
     * 计算连续签到天数
     * 
     * @param userId 用户ID
     * @param currentDate 当前日期
     * @return 连续签到天数
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
     * 
     * @param consecutiveDays 连续签到天数
     * @return 获得积分
     */
    private BigDecimal calculatePointsEarned(int consecutiveDays) {
        BigDecimal basePoints = BigDecimal.ONE; // 基础1积分
        BigDecimal bonusPoints = BigDecimal.ZERO;
        
        // 连续签到奖励
        if (consecutiveDays >= 30) {
            bonusPoints = new BigDecimal("5"); // 连续30天额外5积分
        } else if (consecutiveDays >= 7) {
            bonusPoints = BigDecimal.ONE; // 连续7天额外1积分
        }
        
        return basePoints.add(bonusPoints);
    }
}