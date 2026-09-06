package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.*;
import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.model.UserAchievementClaim;
import chat.liuxin.liutech.resp.*;
import chat.liuxin.liutech.utils.UserUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAchievementService {
    private static final BigDecimal REWARD = new BigDecimal("2.00");
    private final UserAchievementMapper claimMapper;
    private final UserViewHistoryMapper historyMapper;
    private final CommentsMapper commentsMapper;
    private final UserMapper userMapper;
    private final PointsService pointsService;
    private final UserUtils userUtils;

    @Transactional(readOnly = true)
    public List<AchievementResp> list(Long userId) {
        return List.of(describe(userId, "comment_10"), describe(userId, "read_10"));
    }

    private AchievementResp describe(Long userId, String code) {
        String title = switch (code) {
            case "comment_10" -> "热心评论者";
            case "read_10" -> "阅读探索者";
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "成就不存在");
        };
        UserAchievementClaim claimed = claimMapper.selectOne(new LambdaQueryWrapper<UserAchievementClaim>()
                .eq(UserAchievementClaim::getUserId, userId).eq(UserAchievementClaim::getAchievementCode, code));
        long progress = code.equals("comment_10") ? commentsMapper.countVisibleCommentsByUserId(userId) : historyMapper.countVisibleByUserId(userId);
        return new AchievementResp(code, title, claimed != null ? 10 : Math.min(progress, 10), 10,
                claimed != null ? claimed.getRewardPoints() : REWARD, claimed != null ? "claimed" : progress >= 10 ? "claimable" : "in_progress",
                claimed != null ? claimed.getClaimedAt() : null);
    }

    @Transactional(rollbackFor = Exception.class)
    public AchievementClaimResp claim(Long userId, String code) {
        if (claimMapper.lockUser(userId) == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        AchievementResp achievement = describe(userId, code);
        if (achievement.status().equals("in_progress")) throw new BusinessException(ErrorCode.PARAMS_ERROR, "尚未达成领取条件");
        if (!achievement.status().equals("claimed")) {
            UserAchievementClaim claim = new UserAchievementClaim();
            claim.setUserId(userId);
            claim.setAchievementCode(code);
            claim.setRewardPoints(REWARD);
            claim.setClaimedAt(new Date());
            if (claimMapper.insert(claim) != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR);
            // 同一事务：记录、余额、流水任一步失败全部回滚；唯一键作为最终幂等约束。
            pointsService.addPoints(userId, REWARD, PointsService.TYPE_ACHIEVEMENT, PointsService.SOURCE_ACHIEVEMENT, claim.getId(), achievement.title() + "奖励");
            achievement = new AchievementResp(code, achievement.title(), 10, 10, REWARD, "claimed", claim.getClaimedAt());
        }
        var user = userMapper.selectById(userId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { userUtils.clearUserCache(user.getUsername()); }
        });
        return new AchievementClaimResp(achievement, user.getPoints());
    }
}
