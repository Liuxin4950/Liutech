package chat.liuxin.liutech.resp;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 签到月历条目响应类
 * 对应某一天的一条签到记录，用于前端绘制 GitHub 风格的签到日历
 *
 * @author 刘鑫
 * @since 2026-07-31
 */
@Data
@Accessors(chain = true)
public class CheckinCalendarItemResp {

    /**
     * 签到日期
     */
    private LocalDate date;

    /**
     * 当天签到获得积分
     */
    private BigDecimal pointsEarned;
}