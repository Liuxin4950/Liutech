package chat.liuxin.liutech.resp;

import lombok.Data;
import java.util.Date;

@Data
public class UserActivityResp {
    private String id;
    private String type;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Shanghai")
    private Date occurredAt;
    private String title;
    private String targetType;
    private Long targetId;
}
