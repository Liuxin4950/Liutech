package chat.liuxin.liutech.resp;

import java.math.BigDecimal;
import java.util.Date;

public record AchievementResp(String code, String title, long progress, long target,
                              BigDecimal rewardPoints, String status, Date claimedAt) {}
