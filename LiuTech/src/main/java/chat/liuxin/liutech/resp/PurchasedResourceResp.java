package chat.liuxin.liutech.resp;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/** 当前用户购买记录，不返回存储路径、外链或提取码。 */
@Data
public class PurchasedResourceResp {
    private Long id;
    private Long resourceId;
    private String resourceName;
    private String resourceType;
    private BigDecimal pointsUsed;
    private Date purchasedAt;
    private Long postId;
    private String postTitle;
    private Boolean available;
}
