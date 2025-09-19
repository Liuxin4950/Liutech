package chat.liuxin.liutech.resp;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 签到状态响应类
 *
 * @author 刘鑫
 * @since 2025-01-30
 */
@Data
@Accessors(chain = true)
public class CheckinStatusResp {

    /**
     * 今日是否已签到
     */
    private Boolean hasCheckedInToday;

    /**
     * 连续签到天数
     */
    private Integer consecutiveDays;

    /**
     * 最后签到日期
     */
    private LocalDate lastCheckinDate;

    /**
     * 总签到次数
     */
    private Integer totalCheckins;
}
