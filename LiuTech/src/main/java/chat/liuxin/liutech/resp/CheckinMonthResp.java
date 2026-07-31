package chat.liuxin.liutech.resp;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 签到月份响应类
 * 表示用户有过签到记录的某个月份，用于前端月份切换
 *
 * @author 刘鑫
 * @since 2026-07-31
 */
@Data
@Accessors(chain = true)
public class CheckinMonthResp {

    /**
     * 年份
     */
    private Integer year;

    /**
     * 月份（1-12）
     */
    private Integer month;
}