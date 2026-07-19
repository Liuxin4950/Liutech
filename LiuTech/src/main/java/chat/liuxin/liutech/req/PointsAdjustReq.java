package chat.liuxin.liutech.req;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 管理员手动调整积分请求
 *
 * 替代原先 Controller 接收 Map<String,Object> 后手动类型转换。
 *
 * @author 刘鑫
 */
@Data
public class PointsAdjustReq {
    /** 用户ID */
    private Long userId;
    /** 调整金额（正数增加，负数扣减） */
    private BigDecimal amount;
    /** 调整说明 */
    private String description;
}
