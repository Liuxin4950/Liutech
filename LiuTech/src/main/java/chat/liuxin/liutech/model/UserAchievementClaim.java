package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("user_achievement_claims")
public class UserAchievementClaim {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String achievementCode;
    private BigDecimal rewardPoints;
    private Date claimedAt;
}
