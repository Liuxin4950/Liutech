package chat.liuxin.liutech.resl;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 签到响应类
 * 
 * @author 刘鑫
 * @since 2025-01-30
 */
@Data
@Accessors(chain = true)
public class CheckinResl {

    /**
     * 获得积分
     */
    private BigDecimal pointsEarned;

    /**
     * 用户总积分
     */
    private BigDecimal totalPoints;

    /**
     * 连续签到天数
     */
    private Integer consecutiveDays;

    /**
     * 签到日期
     */
    private LocalDate checkinDate;
}