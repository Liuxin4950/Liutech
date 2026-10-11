package chat.liuxin.liutech.resp;

import lombok.Data;

/** 直接聚合 system_logs，全部数字使用与列表相同的筛选条件。 */
@Data
public class LogSummaryResp {
    private long total;
    private long success;
    private long failure;
    private long today;
}
